# Streaming And WebRTC

Use this runbook to verify streaming and WebRTC signaling.

## What To Test

- Start stream through reverse WebSocket.
- Confirm MediaProjection prompt handling when needed.
- Confirm WebRTC offer or connect flow.
- Send answer, ICE, request-frame, and keep-alive through the supported client flow.
- Stop the stream.
- Start a screenshot while streaming.
- Stop while the prompt is visible or during startup, if testing regressions.

## Evidence To Collect

- Stream session ID.
- Reverse WebSocket messages.
- WebRTC offer, ICE, frame, and stop evidence.
- Logs for `WebRtcManager`, `ScreenCaptureService`, `ScreenCaptureActivity`, and `ReverseConnService`.
- Human confirmation that live frames are visible and stop when requested.

## Notes

- For OSS, use reverse WebSocket for streaming.
- Do not treat a synthetic SDP failure as a portal regression.
- After stop, verify no late frame or offer appears.
