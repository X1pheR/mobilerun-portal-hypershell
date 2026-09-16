#!/usr/bin/env python3
"""Build and stage one official Hypershell Mobilerun Portal APK release.

This executor runs inside the dedicated release-executor container. It resolves
only the existing Docker VM Bitwarden Secrets Manager profile, keeps all secret
values inside the process/container boundary, builds from an exact Git source
worktree and stages only the public APK plus non-secret provenance.
"""

from __future__ import annotations

import argparse
import base64
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import ssl
import stat
import subprocess
import sys
import tempfile
from typing import Iterator, Mapping, Sequence

APP_ID = "eu.hypershell.mobilerun.portal"
REPOSITORY = "X1pheR/mobilerun-portal-hypershell"
CANONICAL_REPO = Path("/srv/hypershell/repos/github/X1pheR/mobilerun-portal-hypershell")
ALLOWED_RELEASE_WORKTREE_ROOT = Path("/srv/hypershell/repos/worktrees/X1pheR")
STAGE_ROOT = Path("/srv/hypershell/runtime/mobile-release/mobilerun-portal")
BSM_PROFILES = Path("/srv/hypershell/appdata/mcpjungle/config/bitwarden-secrets-manager-profiles.json")
BSM_PROFILE = "docker-vm"
BSM_PROJECT_NAME = "Docker VM Runtime"
KEYSTORE_SECRET = "MOBILERUN_PORTAL_SIGNING_KEYSTORE_B64"
PASSWORD_SECRET = "MOBILERUN_PORTAL_SIGNING_PASSWORD"
EXECUTOR_IMAGE = "hypershell/mobilerun-portal-release-executor:android35-sdk34-v1"
EXPECTED_CERT_SHA256 = "D7:84:8A:8E:29:C9:AE:5D:C0:1F:BC:A3:C9:DA:4B:87:79:EC:C7:88:B4:23:C6:B6:07:EA:5F:F5:84:D7:BD:AF"
MAX_KEYSTORE_BYTES = 1024 * 1024
MAX_PASSWORD_LENGTH = 512
BUILD_UID = 1000
BUILD_GID = 1000


class ReleaseError(RuntimeError):
    """Expected, deliberately redacted release-executor failure."""


def _sha256_file(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def _run(
    argv: Sequence[str],
    *,
    label: str,
    cwd: Path | None = None,
    env: Mapping[str, str] | None = None,
    sensitive_values: Sequence[str] = (),
) -> subprocess.CompletedProcess[str]:
    rendered = "\0".join(argv)
    for value in sensitive_values:
        if value and value in rendered:
            raise ReleaseError(f"{label} rejected because secret material would enter argv")
    try:
        result = subprocess.run(
            list(argv),
            cwd=str(cwd) if cwd else None,
            env=dict(env) if env is not None else None,
            check=False,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
            text=True,
            encoding="utf-8",
            errors="replace",
        )
    except OSError as exc:
        raise ReleaseError(f"{label} could not start") from exc
    if result.returncode != 0:
        # Child/provider output is deliberately not forwarded. This executor must
        # never become a log-exfiltration route for secret-bearing child processes.
        raise ReleaseError(f"{label} failed with exit code {result.returncode}")
    return result


def _git(repo: Path, *args: str) -> str:
    return _run(
        ["git", "-c", f"safe.directory={repo}", "-C", str(repo), *args],
        label="Git source verification",
    ).stdout.strip()


def _is_allowed_source_path(path: Path) -> bool:
    if path == CANONICAL_REPO:
        return True
    return path.parent == ALLOWED_RELEASE_WORKTREE_ROOT and path.name.startswith(".release-mobilerun-portal-")


def parse_gradle_properties(path: Path) -> dict[str, str]:
    wanted: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line.startswith("#") or "=" not in line:
            continue
        key, value = line.split("=", 1)
        if key in {"versionName", "versionCode"}:
            wanted[key] = value
    if set(wanted) != {"versionName", "versionCode"}:
        raise ReleaseError("gradle.properties is missing versionName/versionCode")
    if not wanted["versionName"] or not wanted["versionCode"].isdigit():
        raise ReleaseError("gradle.properties release identity is invalid")
    return wanted


def verify_source(repo: Path, release_tag: str, expected_sha: str) -> dict[str, str]:
    resolved = repo.resolve(strict=True)
    if not _is_allowed_source_path(resolved):
        raise ReleaseError("source_repo is outside the fixed Mobilerun Portal release-source boundary")
    if re.fullmatch(r"v[0-9]+\.[0-9]+\.[0-9]+", release_tag) is None:
        raise ReleaseError("release_tag must be vMAJOR.MINOR.PATCH")
    if re.fullmatch(r"[0-9a-f]{40}", expected_sha) is None:
        raise ReleaseError("expected_source_sha must be a lowercase 40-character Git SHA")
    head = _git(resolved, "rev-parse", "HEAD")
    if head != expected_sha:
        raise ReleaseError("release source HEAD does not match expected_source_sha")
    if _git(resolved, "status", "--porcelain=v1"):
        raise ReleaseError("release source worktree is not clean")
    tag_sha = _git(resolved, "rev-list", "-n", "1", f"refs/tags/{release_tag}")
    if tag_sha != expected_sha:
        raise ReleaseError("release tag does not resolve to expected_source_sha")
    origin = _git(resolved, "remote", "get-url", "origin")
    accepted_origins = {
        "https://github.com/X1pheR/mobilerun-portal-hypershell.git",
        "git@github.com:X1pheR/mobilerun-portal-hypershell.git",
    }
    if origin not in accepted_origins:
        raise ReleaseError("release source origin is not the maintained public repository")
    properties = parse_gradle_properties(resolved / "gradle.properties")
    return {
        "sourceSha": head,
        "releaseTag": release_tag,
        "versionName": properties["versionName"],
        "versionCode": properties["versionCode"],
    }


def certificate_fingerprint(path: Path) -> str:
    try:
        pem = path.read_text(encoding="ascii")
        der = ssl.PEM_cert_to_DER_cert(pem)
    except (OSError, ValueError, UnicodeError) as exc:
        raise ReleaseError("public signing certificate is invalid") from exc
    digest = hashlib.sha256(der).hexdigest().upper()
    return ":".join(digest[index : index + 2] for index in range(0, len(digest), 2))


def decode_keystore(value: str) -> bytes:
    try:
        payload = base64.b64decode(value, validate=True)
    except (ValueError, TypeError) as exc:
        raise ReleaseError("Bitwarden signing keystore is not valid base64") from exc
    if not payload or len(payload) > MAX_KEYSTORE_BYTES:
        raise ReleaseError("Bitwarden signing keystore size is invalid")
    return payload


def _single_profile_settings_file() -> tempfile.NamedTemporaryFile:
    try:
        payload = json.loads(BSM_PROFILES.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        raise ReleaseError("Bitwarden profile configuration is unavailable or invalid") from exc
    profiles = payload.get("profiles") if isinstance(payload, dict) else None
    profile = profiles.get(BSM_PROFILE) if isinstance(profiles, dict) else None
    if not isinstance(profile, dict):
        raise ReleaseError("Bitwarden Docker VM profile is missing")
    # Preserve the accepted profile definition exactly while avoiding any need to
    # mount unrelated Hermes/OCI Machine Account token files into this container.
    temporary = tempfile.NamedTemporaryFile(
        prefix="portal-bsm-profile-",
        suffix=".json",
        mode="w+",
        encoding="utf-8",
        delete=False,
    )
    os.fchmod(temporary.fileno(), 0o600)
    json.dump({"profiles": {BSM_PROFILE: profile}}, temporary, separators=(",", ":"))
    temporary.write("\n")
    temporary.flush()
    os.fsync(temporary.fileno())
    return temporary


def resolve_signing_material() -> tuple[bytes, str]:
    try:
        from bitwarden_secrets_manager_mcp.config import Settings
        from bitwarden_secrets_manager_mcp.provider import SdkProvider
    except ImportError as exc:
        raise ReleaseError("pinned Bitwarden Secrets Manager dependency is unavailable") from exc

    settings_file = _single_profile_settings_file()
    settings_path = Path(settings_file.name)
    settings_file.close()
    keystore_b64 = ""
    password = ""
    try:
        try:
            settings = Settings.from_file(settings_path, default_file_mode=0o600)
            profile = settings.select_single_profile(BSM_PROFILE, action="Portal release signing")
            provider = SdkProvider(profile)
            projects = provider.assert_expected_scope()
            matches = [item for item in projects if item.get("name") == BSM_PROJECT_NAME]
            if len(matches) != 1:
                raise ReleaseError("Bitwarden profile does not expose the exact expected project")
            project_id = str(matches[0]["id"])
            keystore_b64 = provider.get_secret_value(KEYSTORE_SECRET, project_id)
            password = provider.get_secret_value(PASSWORD_SECRET, project_id)
        except ReleaseError:
            raise
        except Exception as exc:
            raise ReleaseError("Bitwarden signing material could not be resolved") from exc

        if (
            not password
            or len(password) > MAX_PASSWORD_LENGTH
            or any(character in password for character in "\x00\r\n")
        ):
            raise ReleaseError("Bitwarden signing password format is invalid")
        keystore = decode_keystore(keystore_b64)
        return keystore, password
    finally:
        keystore_b64 = ""
        settings_path.unlink(missing_ok=True)


@contextmanager
def private_signing_files(keystore: bytes, password: str) -> Iterator[tuple[Path, Path]]:
    with tempfile.TemporaryDirectory(prefix="portal-signing-") as temporary:
        root = Path(temporary)
        os.chmod(root, 0o700)
        keystore_path = root / "signing.jks"
        password_path = root / "password"
        keystore_path.write_bytes(keystore)
        password_path.write_text(password, encoding="utf-8")
        os.chmod(keystore_path, 0o600)
        os.chmod(password_path, 0o600)
        yield keystore_path, password_path


def parse_key_alias(keytool_output: str) -> str:
    aliases: list[str] = []
    current: str | None = None
    for line in keytool_output.splitlines():
        if line.startswith("Alias name: "):
            current = line.removeprefix("Alias name: ").strip()
        elif line.startswith("Entry type: ") and line.removeprefix("Entry type: ").strip() == "PrivateKeyEntry":
            if current:
                aliases.append(current)
    if len(aliases) != 1 or not aliases[0] or any(character in aliases[0] for character in "\r\n\x00"):
        raise ReleaseError("signing keystore must contain exactly one private-key alias")
    return aliases[0]


def inspect_key_alias(keystore_path: Path, password_path: Path) -> str:
    result = _run(
        [
            "keytool",
            "-list",
            "-v",
            "-keystore",
            str(keystore_path),
            "-storepass:file",
            str(password_path),
        ],
        label="signing keystore inspection",
    )
    return parse_key_alias(result.stdout)


def _linux_process_security_state() -> dict[str, str]:
    try:
        lines = Path("/proc/self/status").read_text(encoding="utf-8").splitlines()
    except OSError as exc:
        raise ReleaseError("Linux process security state is unavailable") from exc
    wanted: dict[str, str] = {}
    for line in lines:
        if line.startswith("CapEff:"):
            wanted["CapEff"] = line.split(":", 1)[1].strip()
        elif line.startswith("NoNewPrivs:"):
            wanted["NoNewPrivs"] = line.split(":", 1)[1].strip()
    if set(wanted) != {"CapEff", "NoNewPrivs"}:
        raise ReleaseError("Linux process security state is incomplete")
    return wanted


def drop_build_privileges(signing_root: Path, keystore_path: Path) -> None:
    if os.geteuid() != 0 or os.getegid() != 0:
        raise ReleaseError("release executor must start as root to resolve the protected Machine Account token")
    try:
        STAGE_ROOT.mkdir(parents=True, exist_ok=True)
        os.chown(STAGE_ROOT, BUILD_UID, BUILD_GID)
        os.chown(signing_root, BUILD_UID, BUILD_GID)
        os.chown(keystore_path, BUILD_UID, BUILD_GID)
        os.setgroups([])
        os.setgid(BUILD_GID)
        os.setuid(BUILD_UID)
    except OSError as exc:
        raise ReleaseError("release executor could not drop to the fixed build identity") from exc
    if os.geteuid() != BUILD_UID or os.getegid() != BUILD_GID:
        raise ReleaseError("release executor build identity verification failed")
    state = _linux_process_security_state()
    if int(state["CapEff"], 16) != 0 or state["NoNewPrivs"] != "1":
        raise ReleaseError("release executor retained privileges after the build-identity drop")


def gradle_release_command() -> list[str]:
    return [
        "./gradlew",
        ":app:assembleRelease",
        "-Pkotlin.compiler.execution.strategy=in-process",
        "--no-daemon",
    ]


def build_release(repo: Path, keystore_path: Path, password: str, alias: str) -> Path:
    env = os.environ.copy()
    env.update(
        {
            "HOME": "/tmp/home",
            "GRADLE_USER_HOME": "/srv/hypershell/cache/gradle-mobile-portal",
            "DROIDRUN_KEYSTORE_PATH": str(keystore_path),
            "DROIDRUN_KEYSTORE_PASSWORD": password,
            "DROIDRUN_KEYSTORE_KEY_ALIAS": alias,
            "DROIDRUN_KEYSTORE_KEY_PASSWORD": password,
        }
    )
    Path(env["HOME"]).mkdir(parents=True, exist_ok=True)
    Path(env["GRADLE_USER_HOME"]).mkdir(parents=True, exist_ok=True)
    _run(
        gradle_release_command(),
        label="official release APK build",
        cwd=repo,
        env=env,
        sensitive_values=(password,),
    )
    properties = parse_gradle_properties(repo / "gradle.properties")
    apk = repo / "app/build/outputs/apk/release" / f"{APP_ID}-{properties['versionName']}-release.apk"
    if not apk.is_file() or apk.stat().st_size < 1:
        raise ReleaseError("release APK was not produced at the expected path")
    return apk


def _android_tool(name: str) -> str:
    android_home = Path(os.environ.get("ANDROID_HOME", ""))
    if not android_home.is_absolute():
        raise ReleaseError("ANDROID_HOME is unavailable")
    if name == "apksigner":
        path = android_home / "build-tools/34.0.0/apksigner"
    elif name == "apkanalyzer":
        candidates = [
            android_home / "cmdline-tools/latest/bin/apkanalyzer",
            android_home / "tools/bin/apkanalyzer",
        ]
        path = next((candidate for candidate in candidates if candidate.is_file()), Path(""))
    else:
        raise ReleaseError("unsupported Android verification tool")
    if not path.is_file():
        found = shutil.which(name)
        if not found:
            raise ReleaseError(f"Android verification tool is unavailable: {name}")
        return found
    return str(path)


def verify_apk(apk: Path, expected_version_name: str, expected_version_code: str) -> str:
    analyzer = _android_tool("apkanalyzer")

    def analyze(field: str) -> str:
        return _run(
            [analyzer, "manifest", field, str(apk)],
            label="release APK manifest verification",
        ).stdout.strip()

    if analyze("application-id") != APP_ID:
        raise ReleaseError("release APK application ID mismatch")
    if analyze("version-name") != expected_version_name:
        raise ReleaseError("release APK versionName mismatch")
    if analyze("version-code") != expected_version_code:
        raise ReleaseError("release APK versionCode mismatch")

    signer = _run(
        [_android_tool("apksigner"), "verify", "--print-certs", str(apk)],
        label="release APK signature verification",
    )
    matches = re.findall(r"Signer #\d+ certificate SHA-256 digest: ([0-9a-fA-F]{64})", signer.stdout)
    if len(matches) != 1:
        raise ReleaseError("release APK must contain exactly one signing certificate")
    digest = matches[0].upper()
    return ":".join(digest[index : index + 2] for index in range(0, len(digest), 2))


def executor_image_id() -> str:
    image_id = os.environ.get("HYPERSHELL_RELEASE_EXECUTOR_IMAGE_ID", "")
    if re.fullmatch(r"sha256:[0-9a-f]{64}", image_id) is None:
        raise ReleaseError("release executor image identity is missing or invalid")
    return image_id


def executor_root() -> Path:
    root = Path(__file__).resolve().parents[1]
    if root != Path("/tool"):
        raise ReleaseError("release executor source is not mounted at the fixed /tool boundary")
    return root


def stage_artifact(
    *,
    apk: Path,
    release_tag: str,
    source_sha: str,
    version_name: str,
    version_code: str,
    cert_fingerprint: str,
    image_id: str,
) -> dict[str, object]:
    release_dir = STAGE_ROOT / release_tag
    release_dir.mkdir(parents=True, exist_ok=True)
    os.chmod(release_dir, 0o755)
    destination = release_dir / f"{APP_ID}-{version_name}.apk"
    digest = _sha256_file(apk)

    if destination.exists():
        mode = destination.lstat().st_mode
        if not stat.S_ISREG(mode) or stat.S_ISLNK(mode):
            raise ReleaseError("staged APK path is not a regular file")
        if _sha256_file(destination) != digest:
            raise ReleaseError("a different APK is already staged for this release tag")
    else:
        with tempfile.NamedTemporaryFile(prefix=".apk-", dir=release_dir, delete=False) as handle:
            temporary = Path(handle.name)
            with apk.open("rb") as source:
                shutil.copyfileobj(source, handle)
            handle.flush()
            os.fsync(handle.fileno())
        try:
            os.chmod(temporary, 0o644)
            os.replace(temporary, destination)
        finally:
            temporary.unlink(missing_ok=True)

    root = executor_root()
    provenance = {
        "schemaVersion": 1,
        "repository": REPOSITORY,
        "releaseTag": release_tag,
        "sourceCommit": source_sha,
        "applicationId": APP_ID,
        "versionName": version_name,
        "versionCode": int(version_code),
        "certificateSha256": cert_fingerprint,
        "apk": destination.name,
        "apkSha256": digest,
        "apkBytes": destination.stat().st_size,
        "releaseExecutorImage": EXECUTOR_IMAGE,
        "releaseExecutorImageId": image_id,
        "releaseExecutorDockerfileSha256": _sha256_file(root / "tools/release-executor/Dockerfile"),
        "releaseExecutorScriptSha256": _sha256_file(Path(__file__).resolve()),
        "createdAt": datetime.now(timezone.utc).isoformat().replace("+00:00", "Z"),
    }
    provenance_path = release_dir / "provenance.json"
    encoded = (json.dumps(provenance, sort_keys=True, indent=2) + "\n").encode("utf-8")
    with tempfile.NamedTemporaryFile(prefix=".provenance-", dir=release_dir, delete=False) as handle:
        temporary = Path(handle.name)
        handle.write(encoded)
        handle.flush()
        os.fsync(handle.fileno())
    try:
        os.chmod(temporary, 0o644)
        os.replace(temporary, provenance_path)
    finally:
        temporary.unlink(missing_ok=True)
    return {**provenance, "apkPath": str(destination), "provenancePath": str(provenance_path)}


def execute(source_repo: Path, release_tag: str, expected_source_sha: str) -> dict[str, object]:
    source = verify_source(source_repo, release_tag, expected_source_sha)
    public_fingerprint = certificate_fingerprint(source_repo / "docs/hypershell-signing-certificate.pem")
    if public_fingerprint != EXPECTED_CERT_SHA256:
        raise ReleaseError("public signing certificate fingerprint does not match the accepted identity")

    image_id = executor_image_id()
    keystore = b""
    password = ""
    try:
        keystore, password = resolve_signing_material()
        with private_signing_files(keystore, password) as (keystore_path, password_path):
            alias = inspect_key_alias(keystore_path, password_path)
            password_path.unlink(missing_ok=True)
            drop_build_privileges(keystore_path.parent, keystore_path)
            apk = build_release(source_repo, keystore_path, password, alias)
            observed_fingerprint = verify_apk(
                apk,
                source["versionName"],
                source["versionCode"],
            )
            if observed_fingerprint != public_fingerprint:
                raise ReleaseError("release APK certificate fingerprint does not match the accepted identity")
            return stage_artifact(
                apk=apk,
                release_tag=release_tag,
                source_sha=source["sourceSha"],
                version_name=source["versionName"],
                version_code=source["versionCode"],
                cert_fingerprint=observed_fingerprint,
                image_id=image_id,
            )
    finally:
        keystore = b""
        password = ""


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--source-repo", required=True)
    parser.add_argument("--release-tag", required=True)
    parser.add_argument("--expected-source-sha", required=True)
    return parser


def main() -> int:
    args = build_parser().parse_args()
    try:
        result = execute(Path(args.source_repo), args.release_tag, args.expected_source_sha)
    except ReleaseError as exc:
        print(json.dumps({"status": "error", "error": str(exc)}, sort_keys=True, separators=(",", ":")))
        return 1
    except Exception:
        print(json.dumps({"status": "error", "error": "unexpected release executor failure"}, sort_keys=True, separators=(",", ":")))
        return 1
    print(json.dumps({"status": "staged", **result}, sort_keys=True, separators=(",", ":")))
    return 0


if __name__ == "__main__":
    sys.exit(main())
