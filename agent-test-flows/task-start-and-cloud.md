# Task Start And Cloud

Use this runbook to verify task start flows.

## What To Test

- Start a task with no stream active.
- Start streaming, then start a task while the stream is active.
- Cancel the task.
- Confirm task status reaches running, canceled, or another terminal state.
- Confirm state requests still work during the task.

## Cloud Checks

Run cloud-backed task checks only when `<mobilerun-api-key-env>` is available. If the app is already connected, use the existing credentials. If setup is needed, the agent may enter the key through the normal UI with taps and text input. Do not print the key, and record whether auth was preexisting or injected for this run.

Prefer the provider automation path when setup is needed:

```bash
MOBILERUN_CLOUD_KEY="${MOBILERUN_API_KEY:-}"
if [ -z "$MOBILERUN_CLOUD_KEY" ] && [ -s /tmp/mobilerun_api_key ]; then
  MOBILERUN_CLOUD_KEY="$(tr -d '\n' < /tmp/mobilerun_api_key)"
fi
if [ -z "$MOBILERUN_CLOUD_KEY" ]; then
  echo "Set MOBILERUN_API_KEY or provide /tmp/mobilerun_api_key" >&2
  exit 1
fi
API_KEY_B64="$(printf '%s' "$MOBILERUN_CLOUD_KEY" | base64 | tr -d '\n')"
PROMPT_B64="$(printf '%s' 'Open Settings and tell me which Android version is installed. Do not change any settings.' | base64 | tr -d '\n')"

adb shell content insert \
  --uri content://com.mobilerun.portal/cloud/connect \
  --bind api_key_base64:s:"$API_KEY_B64"

for attempt in $(seq 1 60); do
  STATUS="$(adb shell content query --uri content://com.mobilerun.portal/cloud/status)"
  if printf '%s' "$STATUS" | grep -q '"connectionState":"CONNECTED"'; then
    break
  fi
  if [ "$attempt" -eq 60 ]; then
    echo "cloud/status did not become CONNECTED within 60 seconds" >&2
    exit 1
  fi
  sleep 1
done
unset MOBILERUN_CLOUD_KEY API_KEY_B64

adb shell content insert \
  --uri content://com.mobilerun.portal/cloud/tasks/launch \
  --bind prompt_base64:s:"$PROMPT_B64"
```

The launch result URI should include `status=success` and `task_id=<id>`. Query `cloud/status` again and confirm the active task entry references the same task id. Keep all command output that might include credentials out of artifacts; only record whether a key was injected, token presence from status, and the redacted task id.

Use a read-only prompt:

```text
Open Settings and tell me which Android version is installed. Do not change any settings.
```

## Evidence To Collect

- Task prompt used.
- UI screenshot or dump before and after task start.
- Reverse messages and task status.
- Stream state if a stream was active.
- Logs for task prompt, cloud client, reverse connection, screenshot, and streaming failures.
