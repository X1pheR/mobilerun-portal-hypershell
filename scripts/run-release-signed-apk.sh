#!/usr/bin/env bash
set -euo pipefail

IMAGE=hypershell/mobilerun-portal-release-executor:android35-sdk34-v1
CONTAINER_BSM_PROFILES=/run/config/bitwarden-profiles.json
CONTAINER_BSM_TOKEN=/run/secrets/bitwarden-token
CONTAINER_GRADLE_CACHE=/gradle-cache

usage() {
  echo "usage: $0 --release-tag vMAJOR.MINOR.PATCH --expected-source-sha <40-char-sha>" >&2
  exit 2
}

require_env() {
  local name=$1
  [[ -n ${!name:-} ]] || {
    echo "required governed release configuration is unavailable: $name" >&2
    exit 1
  }
}

for name in \
  HYPERSHELL_RELEASE_REPO \
  HYPERSHELL_RELEASE_WORKTREE_ROOT \
  HYPERSHELL_RELEASE_BSM_PROFILES \
  HYPERSHELL_RELEASE_BSM_TOKEN \
  HYPERSHELL_RELEASE_GRADLE_CACHE \
  HYPERSHELL_RELEASE_STAGE_ROOT \
  HYPERSHELL_RELEASE_BSM_PROFILE \
  HYPERSHELL_RELEASE_BSM_PROJECT_ID \
  HYPERSHELL_RELEASE_KEYSTORE_SECRET_ID \
  HYPERSHELL_RELEASE_PASSWORD_SECRET_ID \
  HYPERSHELL_RELEASE_BUILD_UID \
  HYPERSHELL_RELEASE_BUILD_GID; do
  require_env "$name"
done

REPO=$HYPERSHELL_RELEASE_REPO
WORKTREE_ROOT=$HYPERSHELL_RELEASE_WORKTREE_ROOT
BSM_PROFILES=$HYPERSHELL_RELEASE_BSM_PROFILES
BSM_TOKEN=$HYPERSHELL_RELEASE_BSM_TOKEN
GRADLE_CACHE=$HYPERSHELL_RELEASE_GRADLE_CACHE
STAGE_ROOT=$HYPERSHELL_RELEASE_STAGE_ROOT
BSM_PROFILE=$HYPERSHELL_RELEASE_BSM_PROFILE
BSM_PROJECT_ID=$HYPERSHELL_RELEASE_BSM_PROJECT_ID
KEYSTORE_SECRET_ID=$HYPERSHELL_RELEASE_KEYSTORE_SECRET_ID
PASSWORD_SECRET_ID=$HYPERSHELL_RELEASE_PASSWORD_SECRET_ID
BUILD_UID=$HYPERSHELL_RELEASE_BUILD_UID
BUILD_GID=$HYPERSHELL_RELEASE_BUILD_GID

for path_name in REPO WORKTREE_ROOT BSM_PROFILES BSM_TOKEN GRADLE_CACHE STAGE_ROOT; do
  value=${!path_name}
  [[ $value == /* && $value != *','* && $value != *$'\n'* && $value != *$'\r'* ]] || {
    echo "governed release path is invalid: $path_name" >&2
    exit 1
  }
done
[[ $BSM_PROFILE =~ ^[A-Za-z0-9._-]+$ ]] || { echo "Bitwarden profile selector is invalid" >&2; exit 1; }
for id in "$BSM_PROJECT_ID" "$KEYSTORE_SECRET_ID" "$PASSWORD_SECRET_ID"; do
  [[ $id =~ ^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$ ]] || {
    echo "Bitwarden scoped identifier is invalid" >&2
    exit 1
  }
done
[[ $BUILD_UID =~ ^[0-9]+$ && $BUILD_GID =~ ^[0-9]+$ ]] || {
  echo "release build identity is invalid" >&2
  exit 1
}

release_tag=
expected_sha=
while [[ $# -gt 0 ]]; do
  case "$1" in
    --release-tag)
      [[ $# -ge 2 ]] || usage
      release_tag=$2
      shift 2
      ;;
    --expected-source-sha)
      [[ $# -ge 2 ]] || usage
      expected_sha=$2
      shift 2
      ;;
    *) usage ;;
  esac
done

[[ $release_tag =~ ^v[0-9]+\.[0-9]+\.[0-9]+$ ]] || usage
[[ $expected_sha =~ ^[0-9a-f]{40}$ ]] || usage

script_root=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")/.." && pwd -P)
[[ $script_root == "$REPO" ]] || {
  echo "release launcher must run from the governed canonical maintained repository" >&2
  exit 1
}

[[ $(git -c safe.directory="$REPO" -C "$REPO" rev-parse --abbrev-ref HEAD) == main ]] || {
  echo "canonical maintained repository must be on main" >&2
  exit 1
}
[[ -z $(git -c safe.directory="$REPO" -C "$REPO" status --porcelain=v1 --untracked-files=all) ]] || {
  echo "canonical maintained repository must be clean" >&2
  exit 1
}
[[ $(git -c safe.directory="$REPO" -C "$REPO" rev-list -n 1 "refs/tags/$release_tag") == "$expected_sha" ]] || {
  echo "release tag does not resolve to expected source SHA" >&2
  exit 1
}

[[ -f $BSM_PROFILES ]] || {
  echo "required protected release profile configuration is unavailable" >&2
  exit 1
}
# Do not stat or read the protected Machine Account token from this unprivileged
# host shell. The Docker daemon owns the bind-source existence/access check below;
# --mount type=bind fails closed when the configured source is unavailable.
for required in "$WORKTREE_ROOT" "$GRADLE_CACHE" "$STAGE_ROOT"; do
  [[ -d $required ]] || {
    echo "required governed release directory is unavailable" >&2
    exit 1
  }
done

release_source="$WORKTREE_ROOT/.release-mobilerun-portal-${release_tag#v}-${expected_sha:0:12}-$$"
cleanup() {
  git -c safe.directory="$REPO" -C "$REPO" worktree remove --force "$release_source" >/dev/null 2>&1 || true
}
trap cleanup EXIT INT TERM HUP
[[ ! -e $release_source ]] || {
  echo "temporary release worktree already exists" >&2
  exit 1
}
git -c safe.directory="$REPO" -C "$REPO" worktree add --detach "$release_source" "$expected_sha" >/dev/null
[[ $(git -c safe.directory="$release_source" -C "$release_source" rev-parse HEAD) == "$expected_sha" ]]
[[ -z $(git -c safe.directory="$release_source" -C "$release_source" status --porcelain=v1 --untracked-files=all) ]]

# The image contains no credentials. Every fetched Python wheel is immutable and
# SHA-256 checked in its Dockerfile before extraction.
docker build --pull=false -q -t "$IMAGE" "$REPO/tools/release-executor" >/dev/null
image_id=$(docker image inspect --format '{{.Id}}' "$IMAGE")
[[ $image_id =~ ^sha256:[0-9a-f]{64}$ ]] || {
  echo "release executor image identity is invalid" >&2
  exit 1
}

# Host-specific paths and Bitwarden selectors are supplied only by the private
# governed wrapper. Secret values are resolved inside the container and never
# cross this shell or Docker argv/environment metadata.
docker run --rm \
  --user 0:0 \
  --read-only \
  --tmpfs /tmp:rw,nosuid,nodev,size=128m \
  --cap-drop ALL \
  --cap-add CHOWN \
  --cap-add SETUID \
  --cap-add SETGID \
  --security-opt no-new-privileges \
  -e "HYPERSHELL_RELEASE_EXECUTOR_IMAGE_ID=$image_id" \
  -e "HYPERSHELL_RELEASE_CANONICAL_REPO=$REPO" \
  -e "HYPERSHELL_RELEASE_WORKTREE_ROOT=$WORKTREE_ROOT" \
  -e "HYPERSHELL_RELEASE_STAGE_ROOT=$STAGE_ROOT" \
  -e "HYPERSHELL_RELEASE_BSM_PROFILE=$BSM_PROFILE" \
  -e "HYPERSHELL_RELEASE_BSM_PROJECT_ID=$BSM_PROJECT_ID" \
  -e "HYPERSHELL_RELEASE_KEYSTORE_SECRET_ID=$KEYSTORE_SECRET_ID" \
  -e "HYPERSHELL_RELEASE_PASSWORD_SECRET_ID=$PASSWORD_SECRET_ID" \
  -e "HYPERSHELL_RELEASE_BUILD_UID=$BUILD_UID" \
  -e "HYPERSHELL_RELEASE_BUILD_GID=$BUILD_GID" \
  --mount "type=bind,src=$REPO,dst=$REPO,readonly" \
  --mount "type=bind,src=$release_source,dst=$release_source" \
  --mount "type=bind,src=$REPO,dst=/tool,readonly" \
  --mount "type=bind,src=$BSM_PROFILES,dst=$CONTAINER_BSM_PROFILES,readonly" \
  --mount "type=bind,src=$BSM_TOKEN,dst=$CONTAINER_BSM_TOKEN,readonly" \
  --mount "type=bind,src=$GRADLE_CACHE,dst=$CONTAINER_GRADLE_CACHE" \
  --mount "type=bind,src=$STAGE_ROOT,dst=$STAGE_ROOT" \
  "$IMAGE" \
  python3 /tool/scripts/release-signed-apk.py \
    --source-repo "$release_source" \
    --release-tag "$release_tag" \
    --expected-source-sha "$expected_sha"
