#!/bin/bash
# 0.31: the arc answers touch (tap «نور», tap a station, tap the line, drag), comes back home by itself;
# the calm Instagram screen opens, refuses Explore/Reels addresses.
mkdir -p shots
exec > >(tee shots/run.log) 2>&1
set -x
P=com.imad.planner
A() { timeout 25 adb "$@"; }
A root; sleep 3
A shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
for k in window_animation_scale transition_animation_scale animator_duration_scale; do A shell settings put global $k 1; done
A shell settings put global auto_time 0
A shell "date 11091130" || true
spot() { A shell uiautomator dump /sdcard/u.xml >/dev/null; A pull /sdcard/u.xml /tmp/u.xml >/dev/null; python3 scripts/find.py /tmp/u.xml "$1"; }
nowait() { local w; w=$(spot "text:Wait"); [ -n "$w" ] && A shell input tap $w && sleep 1; }
tap() { nowait; local xy; xy=$(spot "$1"); echo "tap $1 -> $xy"; [ -n "$xy" ] && A shell input tap $xy; }
shot() { sleep "${2:-4}"; A exec-out screencap -p > "shots/$1.png"; }
go() { A shell am start -S -n $P/.MainActivity --es open "$1"; }
A install -r app/build/outputs/apk/release/app-release.apk
A shell pm grant $P android.permission.POST_NOTIFICATIONS
A shell am start -n $P/.MainActivity; sleep 6; A shell am force-stop $P
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <boolean name="unlocked" value="true" />\n</map>\n' > /tmp/lock.xml
A push /tmp/lock.xml /data/data/$P/shared_prefs/lock.xml
U=$(A shell stat -c %u /data/data/$P | tr -d '\r'); A shell chown $U:$U /data/data/$P/shared_prefs/lock.xml
A shell am start -n $P/.MainActivity; shot 01-today 8
A shell uiautomator dump /sdcard/u.xml; A pull /sdcard/u.xml shots/01.xml
# Where things are on the arc: read from the screenshot by the checker below.
python3 scripts/arcpoints.py shots/01-today.png > shots/arc.txt || true
cat shots/arc.txt
read NX NY SX SY LX LY < shots/arc.txt
# Tap «نور»: the "now" card.
A shell input tap $NX $NY; shot 10-tap-nour 1.5
# Tap a station: «نور» walks there and its card shows.
A shell input tap $SX $SY; sleep 0.15; A exec-out screencap -p > shots/11-walking.png; shot 12-station 1.5
# Tap a plain point on the line.
A shell input tap $LX $LY; shot 13-line 1.5
# Drag along the line from the station back towards the morning.
A shell input swipe $SX $SY $NX $NY 900; shot 14-drag 0.5
# Left alone it walks back home after 8 s.
shot 15-back-home 10
# Tap «نور» twice: open then close.
A shell input tap $NX $NY; sleep 1.5; A shell input tap $NX $NY; shot 16-closed 1.5
# Add from a station's card.
A shell input tap $SX $SY; sleep 1.5; tap "text:+ مهمة بال"; shot 17-add 3
A shell input keyevent 4; sleep 1
# Night colours.
A shell cmd uimode night yes; sleep 2
go tab:0; sleep 7; A shell input tap $NX $NY; shot 20-dark-now 1.5
A shell input tap $SX $SY; shot 21-dark-station 1.5
A shell cmd uimode night no; sleep 2
# The calm Instagram screen.
go ig; shot 30-ig 12
tap "text:الرسائل"; shot 31-ig-messages 10
A shell am start -S -n $P/.IgFocusActivity --es url https://www.instagram.com/explore/; shot 32-ig-explore-refused 12
A shell am start -S -n $P/.IgFocusActivity --es url https://www.instagram.com/reels/; shot 33-ig-reels-refused 12
A shell input keyevent 4; sleep 2; A exec-out screencap -p > shots/34-after-back.png
A logcat -d -t 600 > shots/logcat.txt
A logcat -d -b crash > shots/crash.txt
