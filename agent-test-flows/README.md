# Agent Test Flows

This folder contains generic release-test runbooks for Mobilerun Portal. The files are Markdown only: they are meant to guide agents and humans through repeatable checks.

## Agent Entry Point

If asked to run release tests from this folder:

1. Fill `run-context-template.md` for each connected target.
2. Run `release-checklist.md` on all selected targets, discovering serials, users, API levels, and display sizes with `adb`.
3. Use focused files only when a checklist row needs detail or debugging.
4. Return a compact pass/fail/skip table with artifact paths.

## Target Selection

Use all connected targets unless the request names specific ones. For release coverage, prefer one oldest supported Android version, one middle version, and one latest supported version. Record the actual serial, user, API level, and display size in the run context.

## Interaction Methods

Prefer stable portal APIs, RPC, or content-provider checks when they cover the feature. Use `adb` UI automation, taps, text entry, screenshots, and UI dumps when a check is only reachable through app or system UI. Record the method used and preserve existing credentials or app state unless the row explicitly needs a clean setup.

## Repo Constants

| Field | Value |
| --- | --- |
| Package and content authority | `com.mobilerun.portal` |
| Main activity | `com.mobilerun.portal/.ui.MainActivity` |
| Accessibility service | `com.mobilerun.portal/.service.MobilerunAccessibilityService` |
| Notification listener | `com.mobilerun.portal/.service.MobilerunNotificationListener` |

## Runtime Variables

Fill these at test time. Do not hardcode local paths, emulator serials, Android users, or private credentials in these docs.

| Variable | Meaning |
| --- | --- |
| `<repo-root>` | Local checkout root for this repo |
| `<apk-path>` | Debug or release APK under test |
| `<baseline-apk>` | Previously released APK used for comparison |
| `<adb-serial>` | Device/emulator serial from `adb devices -l` |
| `<android-user-id>` | Current Android user from `am get-current-user` |
| `<api-level>` | Android API level from `getprop ro.build.version.sdk` |
| `<artifact-dir>` | Per-run directory for logs, screenshots, states, and summaries |
| `<mobilerun-cli>` | Local Mobilerun CLI command, if available |
| `<openai-api-key-env>` | Environment variable name containing an OpenAI key |
| `<mobilerun-api-key-env>` | Environment variable name containing a Mobilerun Cloud key |

## Safety Rules

- Never print API keys, bearer tokens, service keys, or their base64 forms.
- Redact secrets in command output, log snippets, and artifact summaries.
- MediaProjection prompt behavior requires screenshot or live UI confirmation plus log evidence.
- Write artifacts under `<artifact-dir>` and keep repo-tracked files unchanged during release verification.

## Optional Checks

- A baseline APK is not required for normal release checks.
- Run `baseline-release-comparison.md` only when a baseline APK is supplied or a regression comparison is requested.
- Run Mobilerun natural-language checks only when an OpenAI key or configured auth is available.
- Run cloud task checks only when a Mobilerun Cloud key is available.

## Manual Confirmation Points

Agents can collect state, logs, UI dumps, screenshots, and JSON-RPC results. A human should confirm:

- Streaming displays live frames and stops when requested.
