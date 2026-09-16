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
            old = module.BSM_PROFILES
            try:
                module.BSM_PROFILES = config
                handle = module._single_profile_settings_file()
                path = Path(handle.name)
                handle.close()
                projected = json.loads(path.read_text(encoding="utf-8"))
                mode = stat.S_IMODE(path.stat().st_mode)
            finally:
                module.BSM_PROFILES = old
                path.unlink(missing_ok=True)
            self.assertEqual(set(projected["profiles"]), {"docker-vm"})
            self.assertEqual(mode, 0o600)

    def test_drop_privileges_fails_closed_when_executor_does_not_start_as_root(self):
        with mock.patch.object(module.os, "geteuid", return_value=1000), mock.patch.object(module.os, "getegid", return_value=1000):
            with self.assertRaisesRegex(module.ReleaseError, "must start as root"):
                module.drop_build_privileges(Path("/tmp/x"), Path("/tmp/x/key"))

    def test_executor_image_id_rejects_untrusted_format(self):
        with mock.patch.dict(module.os.environ, {"HYPERSHELL_RELEASE_EXECUTOR_IMAGE_ID": "latest"}, clear=False):
            with self.assertRaisesRegex(module.ReleaseError, "identity"):
                module.executor_image_id()
        good = "sha256:" + "a" * 64
        with mock.patch.dict(module.os.environ, {"HYPERSHELL_RELEASE_EXECUTOR_IMAGE_ID": good}, clear=False):
            self.assertEqual(module.executor_image_id(), good)

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
