from __future__ import annotations

import base64
import importlib.util
import json
from pathlib import Path
import stat
import tempfile
import unittest
from unittest import mock

SCRIPT = Path(__file__).with_name("release-signed-apk.py")
LAUNCHER = Path(__file__).with_name("run-release-signed-apk.sh")
spec = importlib.util.spec_from_file_location("release_signed_apk", SCRIPT)
module = importlib.util.module_from_spec(spec)
assert spec.loader is not None
spec.loader.exec_module(module)


class ReleaseSignedApkTests(unittest.TestCase):
    def test_decode_keystore_rejects_invalid_and_oversize_payloads(self):
        with self.assertRaisesRegex(module.ReleaseError, "base64"):
            module.decode_keystore("not base64!!")
        payload = b"x" * (module.MAX_KEYSTORE_BYTES + 1)
        with self.assertRaisesRegex(module.ReleaseError, "size"):
            module.decode_keystore(base64.b64encode(payload).decode("ascii"))

    def test_signing_password_normalizes_one_terminal_line_ending_only(self):
        self.assertEqual(module.normalize_signing_password("secret"), "secret")
        self.assertEqual(module.normalize_signing_password("secret\n"), "secret")
        self.assertEqual(module.normalize_signing_password("secret\r"), "secret")
        self.assertEqual(module.normalize_signing_password("secret\r\n"), "secret")
        for value in ("", "\n", "secret\n\n", "secret\nvalue", "secret\x00value"):
            with self.subTest(value=repr(value)):
                with self.assertRaisesRegex(module.ReleaseError, "password format"):
                    module.normalize_signing_password(value)
        with self.assertRaisesRegex(module.ReleaseError, "password format"):
            module.normalize_signing_password("x" * (module.MAX_PASSWORD_LENGTH + 1))

    def test_private_signing_files_are_private_and_removed_after_success(self):
        root = None
        key_path = None
        password_path = None
        with module.private_signing_files(b"keystore", "password") as paths:
            key_path, password_path = paths
            root = key_path.parent
            self.assertEqual(stat.S_IMODE(root.stat().st_mode), 0o700)
            self.assertEqual(stat.S_IMODE(key_path.stat().st_mode), 0o600)
            self.assertEqual(stat.S_IMODE(password_path.stat().st_mode), 0o600)
        assert root is not None and key_path is not None and password_path is not None
        self.assertFalse(root.exists())
        self.assertFalse(key_path.exists())
        self.assertFalse(password_path.exists())

    def test_private_signing_files_are_removed_after_failure(self):
        root = None
        with self.assertRaisesRegex(RuntimeError, "injected"):
            with module.private_signing_files(b"keystore", "password") as paths:
                root = paths[0].parent
                raise RuntimeError("injected")
        assert root is not None
        self.assertFalse(root.exists())

    def test_parse_key_alias_requires_exactly_one_private_key_entry(self):
        output = "Alias name: portal\nEntry type: PrivateKeyEntry\n"
        self.assertEqual(module.parse_key_alias(output), "portal")
        with self.assertRaisesRegex(module.ReleaseError, "exactly one"):
            module.parse_key_alias("")
        with self.assertRaisesRegex(module.ReleaseError, "exactly one"):
            module.parse_key_alias(
                "Alias name: one\nEntry type: PrivateKeyEntry\n"
                "Alias name: two\nEntry type: PrivateKeyEntry\n"
            )

    def test_gradle_argv_contains_no_secret_values(self):
        password = "S3cret-value-that-must-not-enter-argv"
        rendered = "\0".join(module.gradle_release_command())
        self.assertNotIn(password, rendered)
        self.assertNotIn("DROIDRUN_KEYSTORE_PASSWORD=", rendered)
        self.assertNotIn("DROIDRUN_KEYSTORE_KEY_PASSWORD=", rendered)

    def test_run_rejects_secret_material_in_argv_before_process_start(self):
        with self.assertRaisesRegex(module.ReleaseError, "secret material"):
            module._run(
                ["printf", "%s", "top-secret"],
                label="test",
                sensitive_values=("top-secret",),
            )

    def test_parse_gradle_properties_requires_both_release_identity_fields(self):
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary) / "gradle.properties"
            path.write_text("versionName=1.2.3\nversionCode=7\n", encoding="utf-8")
            self.assertEqual(
                module.parse_gradle_properties(path),
                {"versionName": "1.2.3", "versionCode": "7"},
            )
            path.write_text("versionName=1.2.3\n", encoding="utf-8")
            with self.assertRaisesRegex(module.ReleaseError, "missing"):
                module.parse_gradle_properties(path)

    def test_source_boundary_accepts_only_canonical_or_fixed_release_worktree(self):
        canonical = module.CANONICAL_REPO
        allowed = module.ALLOWED_RELEASE_WORKTREE_ROOT / ".release-mobilerun-portal-0.1.0-abc-1"
        rejected = module.ALLOWED_RELEASE_WORKTREE_ROOT / "other-worktree"
        self.assertTrue(module._is_allowed_source_path(canonical))
        self.assertTrue(module._is_allowed_source_path(allowed))
        self.assertFalse(module._is_allowed_source_path(rejected))
        self.assertFalse(module._is_allowed_source_path(Path("/tmp/.release-mobilerun-portal-x")))

    def test_single_profile_projection_excludes_unrelated_machine_accounts(self):
        with tempfile.TemporaryDirectory() as temporary:
            config = Path(temporary) / "profiles.json"
            config.write_text(
                json.dumps(
                    {
                        "profiles": {
                            "docker-vm": {"access_token_file": "/docker-token", "organization_id": "x"},
                            "hermes-vm": {"access_token_file": "/hermes-token", "organization_id": "y"},
                            "oci-vps": {"access_token_file": "/oci-token", "organization_id": "z"},
                        }
                    }
                ),
                encoding="utf-8",
            )
            old_profiles = module.BSM_PROFILES
            old_profile = module.BSM_PROFILE
            path: Path | None = None
            try:
                module.BSM_PROFILES = config
                module.BSM_PROFILE = "docker-vm"
                handle = module._single_profile_settings_file()
                path = Path(handle.name)
                handle.close()
                projected = json.loads(path.read_text(encoding="utf-8"))
                mode = stat.S_IMODE(path.stat().st_mode)
            finally:
                module.BSM_PROFILES = old_profiles
                module.BSM_PROFILE = old_profile
                if path is not None:
                    path.unlink(missing_ok=True)
            self.assertEqual(set(projected["profiles"]), {"docker-vm"})
            self.assertEqual(projected["profiles"]["docker-vm"]["access_token_file"], str(module.BSM_TOKEN))
            self.assertEqual(mode, 0o600)


    def test_public_release_sources_do_not_embed_private_hypershell_secret_paths_or_keys(self):
        root = SCRIPT.parents[1]
        checked = [
            root / "scripts/release-signed-apk.py",
            root / "scripts/run-release-signed-apk.sh",
            root / "docs/RELEASING.md",
        ]
        forbidden = (
            "/srv/hypershell/appdata/mcpjungle",
            "bitwarden-secrets-manager-docker-vm-runtime-token",
            "Docker VM Runtime",
            "MOBILERUN_PORTAL_SIGNING_KEYSTORE_B64",
            "MOBILERUN_PORTAL_SIGNING_PASSWORD",
        )
        for path in checked:
            text = path.read_text(encoding="utf-8")
            for value in forbidden:
                self.assertNotIn(value, text, f"{value!r} leaked into {path.name}")

    def test_drop_privileges_fails_closed_when_executor_does_not_start_as_root(self):
        with mock.patch.object(module.os, "geteuid", return_value=1000), mock.patch.object(module.os, "getegid", return_value=1000):
            with self.assertRaisesRegex(module.ReleaseError, "must start as root"):
                module.drop_build_privileges(Path("/tmp/x"), Path("/tmp/x/key"))

    def test_drop_privileges_transfers_keystore_before_private_directory(self):
        calls = []
        with tempfile.TemporaryDirectory() as temporary:
            base = Path(temporary)
            stage = base / "stage"
            cache = base / "cache"
            signing_root = base / "signing"
            keystore = signing_root / "signing.jks"
            stage.mkdir()
            cache.mkdir()
            signing_root.mkdir()
            keystore.write_bytes(b"x")
            with (
                mock.patch.object(module, "STAGE_ROOT", stage),
                mock.patch.object(module, "GRADLE_CACHE", cache),
                mock.patch.object(module, "BUILD_UID", 1000),
                mock.patch.object(module, "BUILD_GID", 2000),
                mock.patch.object(module.os, "geteuid", side_effect=[0, 1000]),
                mock.patch.object(module.os, "getegid", side_effect=[0, 2000]),
                mock.patch.object(module.os, "chown", side_effect=lambda path, uid, gid: calls.append((Path(path), uid, gid))),
                mock.patch.object(module.os, "setgroups"),
                mock.patch.object(module.os, "setgid"),
                mock.patch.object(module.os, "setuid"),
                mock.patch.object(module, "_linux_process_security_state", return_value={"CapEff": "0000000000000000", "NoNewPrivs": "1"}),
            ):
                module.drop_build_privileges(signing_root, keystore)
        self.assertEqual(calls, [(keystore, 1000, 2000), (signing_root, 1000, 2000)])

    def test_executor_image_id_rejects_untrusted_format(self):
        with mock.patch.dict(module.os.environ, {"HYPERSHELL_RELEASE_EXECUTOR_IMAGE_ID": "latest"}, clear=False):
            with self.assertRaisesRegex(module.ReleaseError, "identity"):
                module.executor_image_id()
        good = "sha256:" + "a" * 64
        with mock.patch.dict(module.os.environ, {"HYPERSHELL_RELEASE_EXECUTOR_IMAGE_ID": good}, clear=False):
            self.assertEqual(module.executor_image_id(), good)

    def test_launcher_delegates_protected_token_path_check_to_docker_bind_mount(self):
        source = LAUNCHER.read_text(encoding="utf-8")
        self.assertIn("[[ -f $BSM_PROFILES ]]", source)
        self.assertNotIn('for required in "$BSM_PROFILES" "$BSM_TOKEN"', source)
        self.assertNotIn("[[ -f $BSM_TOKEN ]]", source)
        self.assertIn('type=bind,src=$BSM_TOKEN,dst=$CONTAINER_BSM_TOKEN,readonly', source)

    def test_stage_artifact_is_idempotent_for_same_bytes_and_rejects_conflict(self):
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            apk = root / "built.apk"
            tool = root / "tool"
            (tool / "tools/release-executor").mkdir(parents=True)
            (tool / "tools/release-executor/Dockerfile").write_text("FROM scratch\n", encoding="utf-8")
            script = tool / "scripts/release-signed-apk.py"
            script.parent.mkdir(parents=True)
            script.write_text("executor", encoding="utf-8")
            apk.write_bytes(b"apk-one")
            old_stage = module.STAGE_ROOT
            try:
                module.STAGE_ROOT = root / "stage"
                with mock.patch.object(module, "executor_root", return_value=tool), mock.patch.object(module, "__file__", str(script)):
                    first = module.stage_artifact(
                        apk=apk,
                        release_tag="v0.1.0",
                        source_sha="a" * 40,
                        version_name="1.0",
                        version_code="1",
                        cert_fingerprint=module.EXPECTED_CERT_SHA256,
                        image_id="sha256:" + "b" * 64,
                    )
                    second = module.stage_artifact(
                        apk=apk,
                        release_tag="v0.1.0",
                        source_sha="a" * 40,
                        version_name="1.0",
                        version_code="1",
                        cert_fingerprint=module.EXPECTED_CERT_SHA256,
                        image_id="sha256:" + "b" * 64,
                    )
                    self.assertEqual(first["apkSha256"], second["apkSha256"])
                    apk.write_bytes(b"apk-two")
                    with self.assertRaisesRegex(module.ReleaseError, "different APK"):
                        module.stage_artifact(
                            apk=apk,
                            release_tag="v0.1.0",
                            source_sha="a" * 40,
                            version_name="1.0",
                            version_code="1",
                            cert_fingerprint=module.EXPECTED_CERT_SHA256,
                            image_id="sha256:" + "b" * 64,
                        )
            finally:
                module.STAGE_ROOT = old_stage


if __name__ == "__main__":
    unittest.main()
