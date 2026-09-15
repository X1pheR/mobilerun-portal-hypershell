# Actions, Input, Files, Clipboard, And Overlay

Use this runbook to smoke-test portal actions that are safe to run on a release candidate.

## What To Test

- Open a stable app, usually Android Settings.
- Run a tap and swipe against coordinates taken from the current accessibility tree.
- Run a safe global action such as back.
- Type text into a safe text field, clear it, and send one key event.
- Set and get clipboard text.
- Show and hide the overlay, and change overlay offset.
- List, upload, download, and delete a small test file in a safe test directory.
- Run fetch or push only when a safe test endpoint exists.

## Evidence To Collect

- Command responses with request IDs.
- Before and after `/state_full?filter=true`.
- UI dump or screenshot when a visible UI change is expected.
- Any `ApiHandler`, `GestureController`, `FileOperations`, or IME logs for failures.

## Notes

- Do not use destructive app or file targets.
- Keep uploaded test data small.
- If a command is blocked by Android permissions, record the permission state instead of forcing it.
