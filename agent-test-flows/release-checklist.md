# Release Checklist

Use this as the top-level checklist before a release.

## Build

- Record commit, branch, and dirty status.
- Run `./gradlew app:testDebugUnitTest --console=plain`.
- Run `./gradlew app:assembleDebug --console=plain`.
- Record APK path and version.

## Device Matrix

- Test the connected targets selected for this release.
- Include old, middle, and latest supported Android versions when available.
- Fill `run-context-template.md` for each target.
- Record whether each row used portal APIs, RPC, content provider, local transport, reverse transport, or UI automation.

## Portal Coverage

- Install, launch, permissions, and service setup.
- Content provider, state, and accessibility.
- Portrait and landscape state.
- Local HTTP and local WebSocket.
- Reverse connection and events.
- Actions, input, files, clipboard, overlay, and keep-awake.
- Screenshot and MediaProjection.
- Streaming and WebRTC.
- Task start with no stream and with stream already active.
- Triggers and notifications.
- Baseline comparison when a release APK is supplied.
- Mobilerun natural-language checks when credentials are available.

## Result Summary

For each row, record pass, fail, or skip, the interaction method, credential state, artifact paths, and a short note.
