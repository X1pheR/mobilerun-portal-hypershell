# Install, Permissions, And Setup

Use this runbook before feature-specific checks.

## Discover Target

Record:

- `<adb-serial>` from `adb devices -l`.
- `<api-level>` from `getprop ro.build.version.sdk`.
- Android release.
- `<android-user-id>` from `am get-current-user`.
- Display size and density.

## Install

- Use update install when credentials need to stay in place.
- Use clean install only for baseline comparison or clean onboarding checks.
- Launch `com.mobilerun.portal/.ui.MainActivity` after install.

## Enable Services

Enable or verify:

- Accessibility service: `com.mobilerun.portal/.service.MobilerunAccessibilityService`.
- Notification listener: `com.mobilerun.portal/.service.MobilerunNotificationListener`, when notification tests are in scope.
- Runtime permissions needed for the selected checks.

## Orientation

Run state checks in portrait and landscape. Record the Android display size and portal state after each change.

## Cleanup

- Stop streams.
- Disable local socket/WebSocket servers if enabled.
- Restore reverse connection config if changed.
- Leave credentials intact unless this was a clean-install test.
