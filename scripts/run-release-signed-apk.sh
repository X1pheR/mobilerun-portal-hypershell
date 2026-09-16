#!/usr/bin/env bash
set -euo pipefail

REPO=/srv/hypershell/repos/github/X1pheR/mobilerun-portal-hypershell
WORKTREE_ROOT=/srv/hypershell/repos/worktrees/X1pheR
BSM_PROFILES=/srv/hypershell/appdata/mcpjungle/config/bitwarden-secrets-manager-profiles.json
BSM_TOKEN=/srv/hypershell/appdata/mcpjungle/secrets/bitwarden-secrets-manager-docker-vm-runtime-token
GRADLE_CACHE=/srv/hypershell/cache/gradle-mobile-portal
STAGE_ROOT=/srv/hypershell/runtime/mobile-release/mobilerun-portal
IMAGE=hypershell/mobilerun-portal-release-executor:android35-sdk34-v1

usage() {
  echo "usage: $0 --release-tag vMAJOR.MINOR.PATCH --expected-source-sha <40-char-sha>" >&2
  exit 2
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
  echo "release launcher must run from the canonical maintained repository" >&2
  exit 1
}

[[ $(git -c safe.directory="$REPO" -C "$REPO" rev-parse --abbrev-ref HEAD) == main ]] || {
  echo "canonical maintained repository must be on main" >&2
  exit 1
}
[[ -z $(git -c safe.directory="$REPO" -C "$REPO" status --porcelain=v1) ]] || {
  echo "canonical maintained repository must be clean" >&2
  exit 1
}
[[ $(git -c safe.directory="$REPO" -C "$REPO" rev-list -n 1 "refs/tags/$release_tag") == "$expected_sha" ]] || {
  echo "release tag does not resolve to expected source SHA" >&2
  exit 1
}

for required in "$BSM_PROFILES" "$BSM_TOKEN"; do
  [[ -f $required ]] || {
    echo "required protected release input is unavailable" >&2
    exit 1
  }
done
[[ -d $GRADLE_CACHE ]] || {
  echo "Gradle release cache is unavailable" >&2
  exit 1
}
[[ -d $STAGE_ROOT ]] || {
  echo "release staging root is unavailable" >&2
  exit 1
}

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
[[ -z $(git -c safe.directory="$release_source" -C "$release_source" status --porcelain=v1) ]]

# The image contains no credentials. Every fetched Python wheel is immutable and
# SHA-256 checked in its Dockerfile before extraction.
docker build --pull=false -q -t "$IMAGE" "$REPO/tools/release-executor" >/dev/null
image_id=$(docker image inspect --format '{{.Id}}' "$IMAGE")
[[ $image_id =~ ^sha256:[0-9a-f]{64}$ ]] || {
  echo "release executor image identity is invalid" >&2
  exit 1
}

# The container starts with only the capabilities needed to read the existing
# mode-0600 Machine Account token and then drop permanently to UID/GID 1000.
# Secret values are resolved inside the container and never cross this shell.
docker run --rm \
  --user 0:0 \
  --read-only \
  --tmpfs /tmp:rw,nosuid,nodev,size=128m \
  --cap-drop ALL \
  --cap-add DAC_OVERRIDE \
  --cap-add CHOWN \
  --cap-add SETUID \
  --cap-add SETGID \
  --security-opt no-new-privileges \
  -e "HYPERSHELL_RELEASE_EXECUTOR_IMAGE_ID=$image_id" \
  --mount "type=bind,src=$REPO,dst=$REPO,readonly" \
  --mount "type=bind,src=$release_source,dst=$release_source" \
  --mount "type=bind,src=$REPO,dst=/tool,readonly" \
  --mount "type=bind,src=$BSM_PROFILES,dst=$BSM_PROFILES,readonly" \
  --mount "type=bind,src=$BSM_TOKEN,dst=$BSM_TOKEN,readonly" \
  --mount "type=bind,src=$GRADLE_CACHE,dst=$GRADLE_CACHE" \
  --mount "type=bind,src=$STAGE_ROOT,dst=$STAGE_ROOT" \
  "$IMAGE" \
  python3 /tool/scripts/release-signed-apk.py \
    --source-repo "$release_source" \
    --release-tag "$release_tag" \
    --expected-source-sha "$expected_sha"
