# Local HTTP And WebSocket

Use this runbook to verify local portal transports.

## What To Test

- Enable local HTTP on a free port.
- Query `ping`, `version`, `state_full`, `packages`, and `screenshot`.
- Enable local WebSocket on a free port.
- Send JSON-RPC commands for `ping`, `state`, and keep-awake.
- Verify local events are received when available.
- Disable both transports and confirm they no longer answer.

## Streaming Note

For OSS, local `stream/start` is expected to be rejected if the current app requires reverse WebSocket for streaming. Record the response and test actual streaming through the reverse connection.

## Evidence To Collect

- Port numbers used.
- HTTP responses.
- WebSocket request and response IDs.
- Screenshot artifact if captured.
- Logs only for failed start, auth, or request rows.
