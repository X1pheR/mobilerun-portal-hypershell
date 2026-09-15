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
