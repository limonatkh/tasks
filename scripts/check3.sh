#!/bin/bash
# 0.27 checks: the day's capacity line, + on each period, the shorter task menu, a focus session for a task.
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

A shell am start -n $P/.MainActivity; shot 01-today 8
A shell input swipe 540 1700 540 900 300; shot 02-today-periods 2
tap "text:+"; shot 03-period-plus 3
A shell input keyevent BACK; sleep 1
go menu; shot 04-menu 4
tap "text:نقل…"; shot 05-menu-move 2
tap "text:إعدادات المهمة"; shot 06-menu-more 2
tap "text:جلسة تركيز"; shot 07-focus 4
A shell input keyevent BACK; sleep 1; shot 08-today-focus 3
A logcat -d -t 600 > shots/logcat.txt
ls -la shots
