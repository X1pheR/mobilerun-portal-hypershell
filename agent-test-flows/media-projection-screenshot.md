# MediaProjection And Screenshot

Use this runbook to verify screenshot capture and MediaProjection prompt behavior.

## What To Test

- Screenshot through HTTP or JSON-RPC.
- Screenshot with overlay hidden.
- Pre-API-30 MediaProjection fallback when applicable.
- API-30+ accessibility screenshot path when applicable.
- Prompt accept, cancel, and second prompt behavior.
- A state request immediately after screenshot.

## Evidence To Collect

- Screenshot image or base64 response.
- API level and screenshot path used.
- UI screenshot or dump if a MediaProjection prompt appears.
- Logs for `ScreenCaptureActivity`, `ScreenCaptureService`, `MediaProjectionAutoAccept`, and `MediaProjScreenshot`.
- Response time or timeout evidence for the follow-up state request.

## Notes

- A denied prompt should not start capture later.
- A completed screenshot should not block later portal requests.
