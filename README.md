# Mobilerun Portal — Hypershell maintained downstream

This repository is a maintained downstream of [droidrun/mobilerun-portal](https://github.com/droidrun/mobilerun-portal), created for self-hosted, on-demand Android computer-use through Hypershell.

It deliberately reuses Portal's mature Android Accessibility/UI-control implementation. The maintained delta is kept small: wake Portal only when a control session is requested, connect outbound to a self-hosted reverse WebSocket bridge, then tear the session down deterministically so Portal does not need a permanent control connection while the phone is idle.

## Why this downstream exists

Upstream Portal supports a configurable reverse WebSocket connection, but its normal reverse-service lifecycle is designed around an explicitly enabled connection. Hypershell needs a different operating model for a daily-driver phone:

- Home Assistant Companion remains the low-power wake/bootstrap plane;
- Portal remains idle when no computer-use session is requested;
- an explicit Home Assistant `command_activity` launches one narrow bootstrap Activity;
- Portal opens a temporary outbound WSS session to a self-hosted bridge;
- reconnect is bounded rather than persistent;
- normal server close or terminal failure performs full cleanup and stops the foreground service.

This repository is not a replacement for the upstream project and is not affiliated with the upstream maintainers. Upstream remains the source for Portal's general Android computer-use functionality.

## Base and provenance

- Upstream: `droidrun/mobilerun-portal`
- Upstream version: `v0.7.25`
- Upstream commit: `d4cb7d6657385488239812e776df584f890e32fd`
- License: GNU AGPL-3.0
- Downstream version: `0.7.25-hypershell.1`
- Android application ID: `eu.hypershell.mobilerun.portal`
- Kotlin namespace: `com.mobilerun.portal` (kept upstream-compatible internally)

See [`UPSTREAM.md`](UPSTREAM.md) for exact provenance and [`HYPERSHELL.md`](HYPERSHELL.md) for the maintained delta.

## Maintained changes

The first Hypershell downstream adds:

- `HypershellSessionActivity`, accepting only `eu.hypershell.mobile.START_SESSION`;
- an on-demand mode for the existing reverse connection service;
- a 120-second recovery budget with capped exponential reconnect backoff;
- normal WebSocket close as a terminal session-complete signal in on-demand mode;
- deterministic cleanup of reverse WSS, screen capture, keep-awake state and the foreground service;
- a distinct Android application ID so upstream Portal and this downstream can coexist and have independent signing/update lineages;
- a maintained update-feed endpoint owned by this repository;
- least-privilege hardening for the selected self-hosted use case: cleartext traffic disabled, the ContentProvider made internal-only, unused SMS/boot triggers disabled, and trigger/install-only permissions removed.

The initial Hypershell control subset intentionally excludes WebRTC, clipboard, APK install, cloud triggers, SMS/notification forwarding and server-side URL fetch/push. Those features are not removed from upstream merely for aesthetics; they are excluded from the maintained control path until a concrete workflow justifies them.

## Architecture

```text
Hypershell
   | reserve session
   v
Self-hosted Mobile Bridge <---- temporary outbound WSS ---- Portal
   ^                                                     ^
   |                                                     |
   +---- Home Assistant ---- FCM / command_activity ------+
```

The phone does not expose an inbound listener to the internet. The Bridge authenticates the device bearer, binds it to Portal's device ID and only admits a new connection when a pending session exists.

The reference self-hosted Bridge used by this downstream is [X1pheR/hypershell-mobile-bridge](https://github.com/X1pheR/hypershell-mobile-bridge).

## Android bootstrap

The bootstrap Activity is intentionally narrow:

- action: `eu.hypershell.mobile.START_SESSION`
- package/application ID: `eu.hypershell.mobilerun.portal`
- no URL, token, command or session ticket is accepted through the Intent;
- the reverse endpoint and bearer must already be configured in Portal application state;
- the Activity is protected by the same `SYSTEM_ALERT_WINDOW` special-permission boundary required by Home Assistant Companion's cross-app `command_activity` flow;
- the Bridge remains the actual authorization boundary through bearer + device-ID + pending-session admission.

A bootstrap request cannot unlock Android keyguard or weaken PIN/biometric security.

## Build and verification

The repository includes a source-owned, digest-pinned Android builder. The canonical verifier supports bounded modes:

```bash
./scripts/verify-hypershell.sh static
./scripts/verify-hypershell.sh tests
./scripts/verify-hypershell.sh apk
```

The maintained candidate is expected to pass the complete upstream/downstream unit suite, APK identity/signature checks, Android lint and the Hypershell security scan before release.

The debug APK produced by `apk` verification is test-only. Official maintained releases use a separate long-lived signing identity so future versions can update the installed downstream app safely.

## Release signing

Android requires every installable APK to be signed, and updates must continue the same signing lineage. Official Hypershell builds therefore use one app-specific long-lived signing key. The private key is never stored in this repository.

Public signing certificate SHA-256 fingerprint:

`D7:84:8A:8E:29:C9:AE:5D:C0:1F:BC:A3:C9:DA:4B:87:79:EC:C7:88:B4:23:C6:B6:07:EA:5F:F5:84:D7:BD:AF`

A copy of the public certificate is included with maintained releases/source so downloaded APKs can be independently verified.

## Upstream functionality

For upstream Portal features, APIs and general project documentation, use the upstream repository directly:

- https://github.com/droidrun/mobilerun-portal
- https://github.com/droidrun/mobilerun-portal/blob/main/docs/reverse-connection.md
- https://github.com/droidrun/mobilerun-portal/blob/main/docs/local-api.md

Some upstream interfaces are intentionally not externally exposed by this downstream. In particular, upstream ADB examples that address `content://com.mobilerun.portal/...` are not a supported external interface here because the ContentProvider is intentionally internal-only.

## License

This downstream preserves the upstream GNU Affero General Public License v3.0. See [`LICENSE`](LICENSE).

Copyright and attribution from upstream are retained. Changes maintained by Hypershell are published under the same AGPL-3.0 terms as required by the upstream license.
