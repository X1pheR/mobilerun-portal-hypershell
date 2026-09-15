# Triggers And Notifications

Use this runbook to verify trigger APIs and notification-related behavior.

## What To Test

- Trigger catalog and status.
- Rule create, list, get, enable, disable, delete.
- Test rule execution.
- Run list, delete, and clear.
- Time-delay trigger.
- App-entered or app-exited trigger.
- Notification-posted or notification-removed trigger when notification listener access is enabled.
- Battery, network, or SMS triggers when the target supports safe simulation.
- Busy policy behavior with a task already running.

## Evidence To Collect

- Trigger status before testing.
- Rule JSON used for each test.
- Runs recorded after each trigger.
- Notification listener state for notification tests.
- Logs for trigger runtime and notification listener failures.

## Notes

- Keep prompts harmless.
- If cloud credentials are missing, still test rule CRUD and record launch rows as skipped.
- Restore battery, network, and notification state after simulation.
