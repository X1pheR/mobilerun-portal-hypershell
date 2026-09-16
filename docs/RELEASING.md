# Maintained release process

Official Hypershell APK releases are intentionally separate from ordinary CI.

## Identities

- Maintained repository release series starts at `v0.1.0`.
- Android `versionName` for that first release is `0.7.25-hypershell.1`, describing the upstream Portal base plus downstream revision.
- Android application ID is `eu.hypershell.mobilerun.portal`.
- Official APKs must be signed by the certificate committed as `docs/hypershell-signing-certificate.pem`.

The repository release version and Android upstream-derived version are deliberately separate: the former versions the maintained Hypershell product, while the latter makes upstream compatibility visible on-device.

## Signing boundary

The private signing key is never committed and is not provided to pull-request CI. A release is published only when the protected signing material is recoverable from the accepted Hypershell secret authority and the built APK verifies against the public certificate fingerprint in the README.

Losing or replacing the private signing key breaks seamless Android updates for already-installed releases, so a debug key is never accepted for maintained installation.

## Source manifest

Regenerate the deterministic maintained-source checksum set only through `./scripts/freeze-source-manifest.sh`. Generated Gradle/IDE state (`build/`, `app/build/`, `.gradle/`, `.idea/`) is deliberately excluded so a fresh checkout can verify the same manifest.

## Release gates

1. `./scripts/verify-hypershell.sh static`
2. full unit suite via `./scripts/verify-hypershell.sh tests`
3. debug APK contract via `./scripts/verify-hypershell.sh apk`
4. Android lint with zero errors and no new maintained-delta findings
5. pinned Trivy vulnerability/secret/misconfiguration gate
6. release APK built from the accepted Git commit with the protected signing identity
7. `apksigner verify --print-certs` matches `docs/hypershell-signing-certificate.pem`
8. release asset SHA-256 and source commit are recorded in GitHub Release metadata

The signing key itself never appears in source, release assets, logs or normal Hypershell Run output.

## Governed local release executor

`scripts/run-release-signed-apk.sh` is the fixed maintained launcher for the protected signing step. It accepts only an already accepted release tag and source commit, for example:

```bash
bash scripts/run-release-signed-apk.sh \
  --release-tag v0.1.0 \
  --expected-source-sha a5c1a2cd4b02aef9ca18dabf06662a0dba78b8fd
```

The launcher must run from a clean canonical `main`. It verifies the tag/commit relationship, creates an isolated detached release-source worktree at the exact accepted commit, builds the dedicated release-executor image from the pinned Android SDK base and starts it with only the fixed source, Gradle cache, release staging, Bitwarden profile and Docker-VM Machine Account token mounts. The executor image contains no credential material; its minimal Bitwarden runtime is installed from immutable SHA-256-verified wheels without a package resolver.

The executor has no secret-valued CLI arguments. It projects only the accepted `docker-vm` Bitwarden Secrets Manager profile, resolves `MOBILERUN_PORTAL_SIGNING_KEYSTORE_B64` and `MOBILERUN_PORTAL_SIGNING_PASSWORD` internally through the official SDK and exact-project scope, and never mounts the Hermes or OCI Machine Account credentials. The container starts privileged only enough to read the existing mode-0600 Docker-VM Machine Account token. After secret resolution and key-alias verification it removes the temporary password file and permanently drops to UID/GID 1000 before Gradle, APK verification and artifact staging. Secret values never cross the host shell, appear in argv, source, normal output, logs or release assets. Temporary signing material is removed on both successful and exceptional exit.

The release source must be clean, its HEAD and release tag must both resolve to the explicitly supplied source SHA, and its origin must be the maintained public repository. The executor validates application ID, Android version, APK signature and certificate continuity, then stages only the public APK plus non-secret `provenance.json` below `/srv/hypershell/runtime/mobile-release/mobilerun-portal/<tag>/`. The provenance records source commit, APK SHA-256, certificate fingerprint, executor image ID and executor source hashes.

The staged artifact is publication input, not a second source authority. GitHub Release publication must still verify the exact tag/source SHA and asset SHA-256 before the immutable release is published.
