# Baseline Release Comparison

Use this runbook when comparing the current APK with a supplied released APK.

## What To Compare

- Version, package, and install result.
- Portrait and landscape `screen_bounds`.
- Filtered and unfiltered accessibility node counts and max bounds.
- Content provider state.
- Screenshot.
- Streaming start and stop.
- Task start with and without active streaming.
- Trigger and notification smoke results.

## Evidence To Collect

Create one table per target:

| Area | Baseline APK | Current APK | Result | Notes |
| --- | --- | --- | --- | --- |
| State and bounds | | | | |
| Screenshot | | | | |
| Streaming | | | | |
| Tasks | | | | |
| Triggers | | | | |

## Notes

- `<baseline-apk>` is a runtime input, not a value stored in this folder.
- If both APKs fail the same way, record it as existing behavior.
- If baseline passes and current fails, record it as a candidate regression.
