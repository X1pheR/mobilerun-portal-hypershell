# Run Context Template

Copy this into `<artifact-dir>/run-context.md` and fill it before testing.

## Repo

| Field | Value |
| --- | --- |
| Repo root | `<repo-root>` |
| Commit | `<git-commit-sha>` |
| Branch | `<git-branch>` |
| APK under test | `<apk-path>` |
| Baseline APK | `<baseline-apk>` or `not used` |
| Package | `com.mobilerun.portal` |
| Content authority | `com.mobilerun.portal` |
| Accessibility service | `com.mobilerun.portal/.service.MobilerunAccessibilityService` |
| Notification listener | `com.mobilerun.portal/.service.MobilerunNotificationListener` |
| Artifact directory | `<artifact-dir>` |

## Target

| Field | Value |
| --- | --- |
| Serial | `<adb-serial>` |
| API level | `<api-level>` |
| Android release | `<android-release>` |
| User ID | `<android-user-id>` |
| Display size | `<display-size>` |

## Credentials

Do not paste secret values.

| Credential | Present? | Source label |
| --- | --- | --- |
| OpenAI key | yes/no | `<openai-api-key-env>` |
| Mobilerun Cloud key | yes/no | `<mobilerun-api-key-env>` |
| Baseline APK | yes/no | `<baseline-apk>` |

## Interaction Notes

| Field | Value |
| --- | --- |
| Methods used | APIs/RPC/content provider/local transport/reverse transport/UI automation |
| Credential state | preexisting/injected for run/not used |
| State cleanup needed | yes/no and short note |
