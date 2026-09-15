# Reverse Connection And Events

Use this runbook to verify reverse WebSocket behavior.

## What To Test

- Configure reverse connection to a local mock server or cloud endpoint.
- Confirm the device connects.
- Send `ping`, `state`, `packages`, clipboard set/get, and one safe action.
- Capture `events/device` messages.
- Close the connection and observe reconnect or disconnect behavior.
- Disable reverse connection after the run.

## Evidence To Collect

- Redacted reverse config.
- Connection open, message, close, and error logs.
- Request and response IDs.
- Event samples.
- State before and after disconnect.

## Notes

- Do not print tokens or service keys.
- If cloud is used, store only redacted artifacts.
- If streaming is active, verify disconnect behavior does not leave capture running.
