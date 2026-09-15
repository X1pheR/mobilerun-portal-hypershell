# Mobilerun Natural-Language Flows

Use this runbook for optional Mobilerun framework checks.

## When To Run

Run only when:

- `<mobilerun-cli>` is available.
- `<openai-api-key-env>` is present or the configured auth mode is available.
- External service use is acceptable for this release run.

Otherwise record the row as skipped.

## Prompts

Use read-only prompts:

- Open Settings, search for "Android version", open the matching result, and tell me the Android version shown. Do not change any settings.
- Open Settings, search for "Display size", open the matching result if it exists, and tell me whether the Display size page is visible. Do not change any settings.

## Evidence To Collect

- Mobilerun command, provider, and model without secrets.
- Exit code and final line.
- Trajectory path.
- Before and after portal state.
- Any portal logs if the run fails because of device timeout, stale state, or missing accessibility nodes.
