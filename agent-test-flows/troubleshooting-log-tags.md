# Troubleshooting Log Tags

Use focused logcat filters when a row fails.

## Useful Tags

| Area | Tags |
| --- | --- |
| Core state | `ApiHandler`, `MobilerunAccessibility`, `MobilerunContentProvider`, `PortalService` |
| Local and reverse transport | `SocketServer`, `PortalWSServer`, `ReverseConnService`, `LocalDeviceEventRelay`, `EventHub` |
| Screenshot and MediaProjection | `ScreenCaptureActivity`, `ScreenCaptureService`, `MediaProjectionAutoAccept`, `MediaProjScreenshot`, `AutoAcceptGate` |
| Streaming | `WebRtcManager`, `ScreenCaptureService`, `ReverseConnService` |
| Triggers and notifications | `TriggerRuntime`, `TriggerApi`, `TriggerAlarmReceiver`, `TriggerSmsReceiver`, `MobilerunNotificationListener`, `EventHub` |
| Tasks and cloud | `TaskPrompt`, `PortalCloudClient`, `TaskPromptNotificationActionReceiver` |
| Files, input, updates | `GestureController`, `FileOperations`, `PackageInstallerAutoAccept`, `KeepAliveService`, `UpdateInstallReceiver` |

## Look For

- Request ID without a response.
- Stale `screen_bounds` after rotation.
- Screenshot or state stuck behind MediaProjection.
- WebRTC offer or frame after stop.
- Reverse disconnect during stream or task start.
- Missing notification access for notification trigger rows.
