#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILDER="hypershell/mobilerun-portal-builder:android35-sdk34-v1"
if [[ -n "${HYPERSHELL_GRADLE_CACHE:-}" ]]; then
  CACHE="$HYPERSHELL_GRADLE_CACHE"
elif [[ -d /srv/hypershell/cache && -w /srv/hypershell/cache ]]; then
  CACHE=/srv/hypershell/cache/gradle-mobile-portal
else
  CACHE="${XDG_CACHE_HOME:-$HOME/.cache}/hypershell/mobilerun-portal-gradle"
fi
MODE="${1:-all}"

static_checks() {
  grep -q 'd4cb7d6657385488239812e776df584f890e32fd' "$ROOT/UPSTREAM.md"
  grep -q '^versionName=0.7.25-hypershell.1$' "$ROOT/gradle.properties"
  grep -q '^versionCode=96$' "$ROOT/gradle.properties"
  grep -q '<string name="app_name">Mobilerun Portal (Hypershell)</string>' "$ROOT/app/src/main/res/values/strings.xml"
  cert_fp="$(openssl x509 -in "$ROOT/docs/hypershell-signing-certificate.pem" -noout -fingerprint -sha256 | cut -d= -f2)"
  [[ "$cert_fp" == 'D7:84:8A:8E:29:C9:AE:5D:C0:1F:BC:A3:C9:DA:4B:87:79:EC:C7:88:B4:23:C6:B6:07:EA:5F:F5:84:D7:BD:AF' ]] || { echo "unexpected signing certificate fingerprint" >&2; exit 1; }
  python3 - "$ROOT/app/src/main/AndroidManifest.xml" <<'PY'
import sys, xml.etree.ElementTree as ET
p=sys.argv[1]
root=ET.parse(p).getroot()
a='{http://schemas.android.com/apk/res/android}'
activities=root.findall('./application/activity')
matches=[x for x in activities if x.get(a+'name')=='.hypershell.HypershellSessionActivity']
assert len(matches)==1
activity=matches[0]
assert activity.get(a+'exported')=='true'
assert activity.get(a+'excludeFromRecents')=='true'
assert activity.get(a+'noHistory')=='true'
assert activity.get(a+'permission')=='android.permission.SYSTEM_ALERT_WINDOW'
actions=[x.get(a+'name') for x in activity.findall('./intent-filter/action')]
assert actions==['eu.hypershell.mobile.START_SESSION']
categories=[x.get(a+'name') for x in activity.findall('./intent-filter/category')]
assert categories==['android.intent.category.DEFAULT']
assert not activity.findall('./intent-filter/data')
app=root.find('./application')
assert app is not None
assert app.get(a+'usesCleartextTraffic')=='false'
main=[x for x in app.findall('activity') if x.get(a+'name')=='.ui.MainActivity']
assert len(main)==1
all_actions=[a_.get(a+'name') for f in main[0].findall('intent-filter') for a_ in f.findall('action')]
assert 'android.intent.action.VIEW' not in all_actions
for f in main[0].findall('intent-filter'):
    assert not f.findall('data')
provider=[x for x in app.findall('provider') if x.get(a+'name')=='.service.MobilerunContentProvider']
assert len(provider)==1 and provider[0].get(a+'exported')=='false'
for name in ('.triggers.TriggerBootReceiver', '.triggers.TriggerSmsReceiver'):
    xs=[x for x in app.findall('receiver') if x.get(a+'name')==name]
    assert len(xs)==1 and xs[0].get(a+'enabled')=='false' and xs[0].get(a+'exported')=='false'
relaunch=[x for x in app.findall('receiver') if x.get(a+'name')=='.update.UpdateRelaunchReceiver']
assert len(relaunch)==1 and relaunch[0].get(a+'exported')=='false'
permissions={x.get(a+'name') for x in root.findall('uses-permission')}
for denied in (
    'android.permission.RECEIVE_BOOT_COMPLETED',
    'android.permission.RECEIVE_SMS',
    'android.permission.READ_CONTACTS',
    'android.permission.REQUEST_INSTALL_PACKAGES',
    'android.permission.SCHEDULE_EXACT_ALARM',
):
    assert denied not in permissions
PY
  if [[ -f "$ROOT/SOURCE-MANIFEST.sha256" ]]; then
    (cd "$ROOT" && sha256sum -c SOURCE-MANIFEST.sha256 >/dev/null)
  fi
  echo 'static_checks=passed'
}

prepare_builder() {
  docker build --pull=false -q -t "$BUILDER" "$ROOT/tools/android-builder" >/dev/null
  mkdir -p "$CACHE"
}

gradle_in_builder() {
  local task="$1"
  local uid gid
  uid="$(id -u)"; gid="$(id -g)"
  docker run --rm --user "$uid:$gid" \
    -e HOME=/tmp/home -e GRADLE_USER_HOME=/gradle-cache \
    -v "$ROOT:/work" -v "$CACHE:/gradle-cache" -w /work "$BUILDER" \
    bash -lc "mkdir -p \"\$HOME\" && ./gradlew $task -Pkotlin.compiler.execution.strategy=in-process --no-daemon"
}

verify_tests() {
  prepare_builder
  gradle_in_builder ':app:testDebugUnitTest'
  python3 - "$ROOT/app/build/test-results/testDebugUnitTest" <<'PY'
from pathlib import Path
import sys, xml.etree.ElementTree as ET
root=Path(sys.argv[1])
t=f=e=s=files=0
for p in root.glob('TEST-*.xml'):
    files += 1
    r=ET.parse(p).getroot()
    t += int(r.attrib.get('tests',0)); f += int(r.attrib.get('failures',0))
    e += int(r.attrib.get('errors',0)); s += int(r.attrib.get('skipped',0))
assert files > 0 and t > 0 and f == 0 and e == 0
print(f'test_files={files} tests={t} failures={f} errors={e} skipped={s}')
PY
}

verify_apk() {
  prepare_builder
  gradle_in_builder ':app:assembleDebug'
  local apk="$ROOT/app/build/outputs/apk/debug/eu.hypershell.mobilerun.portal-0.7.25-hypershell.1-debug.apk"
  [[ -f "$apk" ]] || { echo "debug APK not found: $apk" >&2; exit 1; }
  local uid gid
  uid="$(id -u)"; gid="$(id -g)"
  docker run --rm --user "$uid:$gid" -v "$ROOT:/work:ro" -w /work "$BUILDER" bash -lc '
    set -euo pipefail
    APK=/work/app/build/outputs/apk/debug/eu.hypershell.mobilerun.portal-0.7.25-hypershell.1-debug.apk
    test "$(apkanalyzer manifest application-id "$APK")" = "eu.hypershell.mobilerun.portal"
    test "$(apkanalyzer manifest version-name "$APK")" = "0.7.25-hypershell.1"
    test "$(apkanalyzer manifest version-code "$APK")" = "96"
    signer="$ANDROID_HOME/build-tools/34.0.0/apksigner"
    "$signer" verify "$APK"
    "$signer" verify --print-certs "$APK" | grep -q "CN=Android Debug"
    apkanalyzer manifest print "$APK" | grep -q "com.mobilerun.portal.hypershell.HypershellSessionActivity"
  '
  echo 'apk_signing=debug-test-only'
  printf 'apk=%s\n' "$apk"
  sha256sum "$apk"
}

case "$MODE" in
  static) static_checks ;;
  tests) static_checks; verify_tests ;;
  apk) static_checks; verify_apk ;;
  all) static_checks; verify_tests; verify_apk ;;
  *) echo "usage: $0 [static|tests|apk|all]" >&2; exit 2 ;;
esac
