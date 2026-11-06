#!/bin/bash
# 0.30 «رحلة اليوم»: every tab in the new look, the task card, the done tick and its cheer, levels, dark mode,
# black-and-white, calm motion. Animations are on, and the moments are recorded as video.
mkdir -p shots
exec > >(tee shots/run.log) 2>&1
set -x
P=com.imad.planner
A() { timeout 25 adb "$@"; }
A root; sleep 3
A shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
A shell settings put secure immersive_mode_confirmations confirmed
for k in window_animation_scale transition_animation_scale animator_duration_scale; do A shell settings put global $k 1; done
A shell settings put global auto_time 0
A shell "date 11061430" || true
spot() { A shell uiautomator dump /sdcard/u.xml >/dev/null; A pull /sdcard/u.xml /tmp/u.xml >/dev/null; python3 scripts/find.py /tmp/u.xml "$1"; }
nowait() { local w; w=$(spot "text:Wait"); [ -n "$w" ] && A shell input tap $w && sleep 1; }
tap() { nowait; local xy; xy=$(spot "$1"); echo "tap $1 -> $xy"; [ -n "$xy" ] && A shell input tap $xy; }
shot() { sleep "${2:-4}"; nowait; A exec-out screencap -p > "shots/$1.png"; }
rec() { A shell screenrecord --time-limit "$2" /sdcard/$1.mp4 & }
pullrec() { sleep 2; A pull /sdcard/$1.mp4 shots/$1.mp4 || true; }
A install -r app/build/outputs/apk/release/app-release.apk
A shell pm grant $P android.permission.POST_NOTIFICATIONS
A shell am start -n $P/.MainActivity; sleep 8; shot 00-lock 2; A shell am force-stop $P
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <boolean name="unlocked" value="true" />\n</map>\n' > /tmp/lock.xml
A push /tmp/lock.xml /data/data/$P/shared_prefs/lock.xml
U=$(A shell stat -c %u /data/data/$P | tr -d '\r'); A shell chown $U:$U /data/data/$P/shared_prefs/lock.xml

A shell am start -n $P/.MainActivity; shot 01-today 8
A shell uiautomator dump /sdcard/u.xml; A pull /sdcard/u.xml shots/01-today.xml
A shell input swipe 540 1800 540 1000 400; shot 02-today-scroll 2
A shell input swipe 540 1000 540 1800 400; sleep 1
# The done tick: record the moment, then the screen after it.
rec 10-done 6; sleep 1; tap "desc:منجزة"; sleep 5; pullrec 10-done; shot 11-after-done 1
tap "desc:منجزة"; shot 12-second-done 3
tap "desc:منجزة"; shot 13-third-done 3
# The task card (bottom sheet): record it opening.
rec 20-card 4; sleep 1; tap "chip-right"; sleep 3; pullrec 20-card; shot 21-card 1
tap "text:نقل"; shot 22-card-move 2
A shell input keyevent 4; sleep 1
# The four tabs.
tap "text:التخطيط"; shot 30-planning 3
tap "text:الأسبوع"; shot 31-week 3
tap "text:الأهداف"; shot 40-goals 4
A shell input swipe 540 1700 540 900 400; shot 41-goals-scroll 2
A shell input swipe 540 900 540 1700 400; sleep 1
tap "text:مستواك العام"; shot 42-progress-sheet 3
A shell input swipe 540 1800 540 700 400; shot 43-progress-sheet-scroll 2
A shell input keyevent 4; sleep 1
tap "text:المراجعة"; shot 50-review 3
# Settings sheet.
tap "desc:الإعدادات"; shot 60-settings 3
A shell input swipe 540 1900 540 400 300; A shell input swipe 540 1900 540 400 300; shot 61-settings-end 2
A shell input keyevent 4; sleep 1
tap "text:اليوم"; sleep 2
# Dark mode.
A shell cmd uimode night yes; sleep 3
A shell am start -S -n $P/.MainActivity; shot 70-dark-today 8
tap "text:الأهداف"; shot 71-dark-goals 4
tap "chip-right"; sleep 1; tap "text:اليوم"; shot 72-dark-today2 3
tap "chip-right"; shot 73-dark-card 3
A shell input keyevent 4
A shell cmd uimode night no; sleep 2
# Black and white.
A shell am force-stop $P
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <boolean name="mono" value="true" />\n</map>\n' > /tmp/ui.xml
A push /tmp/ui.xml /data/data/$P/shared_prefs/ui.xml; A shell chown $U:$U /data/data/$P/shared_prefs/ui.xml
A shell am start -n $P/.MainActivity; shot 80-mono-today 8
tap "text:الأهداف"; shot 81-mono-goals 4
# Night: the sleeping lantern.
A shell am force-stop $P; A shell rm /data/data/$P/shared_prefs/ui.xml
A shell "date 11062340" || true
A shell am start -n $P/.MainActivity; shot 90-night 8
# Larger text.
A shell settings put system font_scale 1.3; sleep 2
A shell "date 11061000" || true
A shell am start -S -n $P/.MainActivity; shot 95-big-text 8
A shell settings put system font_scale 1.0
A logcat -d -t 800 > shots/logcat.txt
A logcat -d -b crash > shots/crash.txt
