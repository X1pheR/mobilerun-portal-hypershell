# Hypershell downstream delta

This is a maintained downstream candidate of **Mobilerun Portal v0.7.25** for self-hosted, on-demand Android computer use.

## Why this downstream exists

Upstream Portal already provides the difficult Android functionality: Accessibility-based UI state and actions, screenshots, keyboard input, app/file operations and an outbound reverse WebSocket client. Hypershell does not reimplement those features.

The Hypershell architecture requires a different lifecycle: Portal should normally be idle, Home Assistant Companion should wake/bootstrap it only when a control session is requested, and Portal must stop again after the session or a short recovery budget. Upstream v0.7.25 is designed around a user-enabled reverse service with a much longer reconnect window.

## Maintained delta

The current delta intentionally consists of only:

- `HypershellSessionActivity`, an exported transparent bootstrap Activity accepting only `eu.hypershell.mobile.START_SESSION` and requiring the caller to hold Android's `SYSTEM_ALERT_WINDOW` special permission; this aligns with Home Assistant Companion's existing `command_activity` requirement rather than introducing another permission path;
- a bootstrap policy requiring a preconfigured custom `wss://` endpoint and bearer token, with a 30-second local start rate limit;
- a distinct on-demand mode in `ReverseConnectionService` using `START_NOT_STICKY`;
- reconnect backoff of 1, 2, 4, 8, 16 and then 30 seconds, with a 120-second total recovery budget;
- normal WebSocket close code `1000` as intentional on-demand session completion;
- deterministic cleanup of capture, keep-awake, WebSocket, reconnect callbacks and the foreground service;
- no change to normal upstream reverse-service behavior outside the on-demand action path;
- first-PoC least-privilege hardening: the upstream ContentProvider is internal-only, SMS/boot trigger receivers are disabled and non-exported, trigger-only SMS/contact/boot/exact-alarm permissions are removed, package-install permission is removed, the package-replaced receiver is non-exported, and cleartext outbound traffic is disabled.

No URL, bearer token or session ticket is accepted through the exported Activity. Server-side authorization remains responsible for admitting only a known device with the correct bearer and a pending session.

## Licensing and upstream

The upstream project is AGPL-3.0. This downstream retains the upstream license and provenance. Any public release must keep corresponding source available under the applicable AGPL terms and must clearly identify downstream changes without implying endorsement by the upstream project.

See `UPSTREAM.md` for the exact upstream commit.

## APK signing boundary

The pre-release verifier builds a **debug-signed test APK only**. The containerized Android build environment does not persist the default Android debug keystore, so that signature is not a maintained update identity and must not be treated as a production/public release signature.

Before the first maintained APK release or installation path that must support in-place upgrades, create and protect one stable downstream signing identity outside maintained source, wire release signing to that identity, verify certificate continuity, and retain recovery material. Do not reuse or commit an Android debug keystore.

## Android application identity

The maintained downstream uses application ID `eu.hypershell.mobilerun.portal` while retaining the upstream Kotlin namespace `com.mobilerun.portal`. This deliberately separates installation/update signing lineage from upstream Mobilerun Portal and allows both applications to coexist if needed. The maintained update feed is owned by `X1pheR/mobilerun-portal-hypershell`.
The launcher label is `Mobilerun Portal (Hypershell)`, and the upstream `mobilerun://` / `droidrun://` cloud-auth callback intent filters are intentionally not claimed by this self-hosted downstream.
