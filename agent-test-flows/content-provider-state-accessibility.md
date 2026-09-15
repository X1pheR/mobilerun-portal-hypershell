# Content Provider, State, And Accessibility

Use this runbook to verify the content provider and accessibility state exposed by the portal.

## What To Test

- `ping`, `version`, `phone_state`, and `packages`.
- `state` and `state_full` with filtered and unfiltered trees.
- `a11y_tree` and `a11y_tree_full` with filtered and unfiltered trees.
- Clipboard set and get through the provider.
- Overlay visibility and overlay offset.
- Screen keep-awake status and toggle.
- Socket, WebSocket, reverse connection, no-a11y, and trigger provider endpoints.
- Cloud provider endpoints: `cloud/status` always, plus `cloud/connect` and `cloud/tasks/launch` when a Mobilerun Cloud API key is available.

## Orientation Check

Run the state checks in portrait and landscape.

Record:

- Display size from Android.
- Portal `screen_bounds`.
- Filtered node count.
- Unfiltered node count.
- Max `right` and `bottom` bounds.
- Foreground package and activity.

## Evidence To Collect

- Raw provider responses.
- Parsed summary table.
- `dumpsys display` or window display output.
- Logcat only for failing rows.
- For cloud provider rows, record only token presence, connection state, and redacted task ids.

## Notes

- Do not hardcode expected display sizes.
- The important regression signal is stale bounds or missing visible nodes after rotation.
