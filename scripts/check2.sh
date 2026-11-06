#!/bin/bash
# 0.26 checks: upgrade from 0.25 keeps the data, real time past the estimate, the time fix, the one postpone rule,
# the close-day preview, and the days / real hours choice.
mkdir -p shots
exec > >(tee shots/run.log) 2>&1
set -x
P=com.imad.planner
A() { timeout 25 adb "$@"; }
A root; sleep 3
A shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
A shell settings put secure immersive_mode_confirmations confirmed
spot() { A shell uiautomator dump /sdcard/u.xml >/dev/null; A pull /sdcard/u.xml /tmp/u.xml >/dev/null; python3 scripts/find.py /tmp/u.xml "$1"; }
nowait() { local w; w=$(spot "text:Wait"); [ -n "$w" ] && A shell input tap $w && sleep 1; }
tap() { nowait; local xy; xy=$(spot "$1"); echo "tap $1 -> $xy"; [ -n "$xy" ] && A shell input tap $xy; }
shot() { sleep "${2:-4}"; nowait; A exec-out screencap -p > "shots/$1.png"; }
go() { A shell am start -S -n $P/.MainActivity --es open "$1"; }
unlock() {
  printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <boolean name="unlocked" value="true" />\n</map>\n' > /tmp/lock.xml
  A shell am force-stop $P
  A push /tmp/lock.xml /data/data/$P/shared_prefs/lock.xml
  U=$(A shell stat -c %u /data/data/$P | tr -d '\r'); A shell chown $U:$U /data/data/$P/shared_prefs/lock.xml
}

# 1. Upgrade: 0.25 first, with its data, then 0.26 over it.
if [ -s old.apk ]; then
  A install old.apk; A shell pm grant $P android.permission.POST_NOTIFICATIONS; A shell am start -n $P/.MainActivity; sleep 10; unlock
  A shell am start -n $P/.MainActivity; shot 01-old-0.25 8
  A shell am force-stop $P
fi
A install -r app/build/outputs/apk/release/app-release.apk
A shell pm grant $P android.permission.POST_NOTIFICATIONS
[ -s old.apk ] || unlock
A logcat -c
A shell am start -n $P/.MainActivity; shot 02-after-upgrade 10
A logcat -d | grep -E "FATAL|AndroidRuntime|IllegalState|Migration" > shots/upgrade-errors.txt || true

# 2. The time fix, and the timer past the estimate.
go menu; shot 03-menu 4
tap "text:تصحيح الوقت"; shot 04-fix-dialog 2
xy=$(spot "text:30"); A shell input tap $(spot "text:الوقت الحقيقي" || echo "540 1100")
A shell input keyevent KEYCODE_MOVE_END; for i in 1 2 3 4; do A shell input keyevent KEYCODE_DEL; done
A shell input text 45; shot 05-fix-typed 1
tap "text:حفظ"; shot 06-after-fix 3
go tab:0; shot 07-today-over 5

# 3. Postponing a recurring task: series shifts by default.
go postpone; shot 08-postpone 4

# 4. Closing the day: what tomorrow gets.
go askclose; shot 09-close-preview 4
A shell input swipe 540 1900 540 800 300; shot 10-close-preview-2 2

# 5. Days or real hours.
go rule48; shot 11-rule 4
for k in 1 2 3; do A shell input swipe 540 1900 540 900 300; sleep 1; done; shot 12-rule-repeat 2
tap "text:ساعة فعلية"; shot 13-real-hours 2
A logcat -d -t 600 > shots/logcat.txt
ls -la shots
