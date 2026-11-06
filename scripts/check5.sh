#!/bin/bash
# 0.28 checks: the four tabs, postpone reasons, the prayer sheet (template and preview).
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
A install -r app/build/outputs/apk/release/app-release.apk
A shell pm grant $P android.permission.POST_NOTIFICATIONS
A shell am start -n $P/.MainActivity; sleep 8; A shell am force-stop $P
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <boolean name="unlocked" value="true" />\n</map>\n' > /tmp/lock.xml
A push /tmp/lock.xml /data/data/$P/shared_prefs/lock.xml
U=$(A shell stat -c %u /data/data/$P | tr -d '\r'); A shell chown $U:$U /data/data/$P/shared_prefs/lock.xml

go prayers; shot 08-prayers 4
for k in 1 2 3 4 5 6 7 8; do xy=$(spot "text:قالب الشهر الجاي"); echo "try $k -> $xy"; if [ -n "$xy" ]; then A shell input tap $xy; break; fi; A shell input swipe 540 1600 540 1300 500; sleep 2; done
shot 10-template 3
A shell input swipe 540 1800 540 1300 600; sleep 2; shot 11-template-preview 1
A shell input swipe 540 1800 540 1300 600; sleep 2; shot 12-template-preview-2 1
A logcat -d -t 600 > shots/logcat.txt
