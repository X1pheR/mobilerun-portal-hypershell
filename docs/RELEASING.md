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

`scripts/run-release-signed-apk.sh` is the product-owned fixed launcher for the protected signing step. The supported operator interface is the governed Hypershell release tool, which supplies the exact accepted release tag/source commit plus host-specific non-secret configuration. Direct invocation without that governed configuration fails closed.

The launcher verifies clean canonical source and tag/commit identity, creates an isolated detached release-source worktree at the exact accepted commit, builds the dedicated release-executor image from the pinned Android SDK base and starts it with only the required source, Gradle cache, release staging, selected Bitwarden profile configuration and one Machine Account token mount. The public repository deliberately contains no Homelab-specific secret-store paths, project names, secret identifiers or credential values.

The executor has no secret-valued CLI arguments or Docker environment values. The private governed wrapper supplies only non-secret selectors for one already-scoped Bitwarden profile/project and the two signing-secret records. The executor resolves their values internally through the official Bitwarden SDK, replaces the profile's host token path with the fixed container-local token mount, and never receives unrelated Machine Account credentials. It starts with only the capabilities needed to read the protected token, hand ownership of temporary signing material to the configured unprivileged build identity, and then permanently drops privileges before Gradle, APK verification and artifact staging. Secret values never cross the host shell, enter process argv/Docker metadata, appear in source, normal output, logs or release assets. Temporary signing material is removed on both successful and exceptional exit.

The release source must be clean, its HEAD and release tag must both resolve to the explicitly supplied source SHA, and its origin must be the maintained public repository. The executor validates application ID, Android version, APK signature and certificate continuity, then stages only the public APK plus non-secret `provenance.json` below the governed release staging root. Provenance records source commit, APK SHA-256, certificate fingerprint, executor image ID and executor source hashes.

The staged artifact is publication input, not a second source authority. GitHub Release publication must still verify the exact tag/source SHA and asset SHA-256 before the immutable release is published.
