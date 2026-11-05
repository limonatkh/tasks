#!/bin/bash
# This round's checks: day planning, closing at night and opening again, a yearly plan from zero, Instagram's answer, swipes.
set -x
mkdir -p shots
P=com.imad.planner
A() { timeout 25 adb "$@"; }
A install -r app/build/outputs/apk/release/app-release.apk
A shell pm grant $P android.permission.POST_NOTIFICATIONS || true
A root; sleep 3
A shell am start -n $P/.MainActivity; sleep 6; A shell am force-stop $P
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <boolean name="unlocked" value="true" />\n</map>\n' > /tmp/lock.xml
A push /tmp/lock.xml /data/data/$P/shared_prefs/lock.xml
U=$(A shell stat -c %u /data/data/$P | tr -d '\r'); A shell chown $U:$U /data/data/$P/shared_prefs/lock.xml
A shell settings put secure immersive_mode_confirmations confirmed
A shell settings put global auto_time 0
shot() { sleep "${2:-4}"; nowait; A exec-out screencap -p > "shots/$1.png"; }
go() { A shell am start -S -n $P/.MainActivity --es open "$1"; }
spot() { A shell uiautomator dump /sdcard/u.xml >/dev/null; A pull /sdcard/u.xml /tmp/u.xml >/dev/null; python3 scripts/find.py /tmp/u.xml "$1"; }
# The launcher's "isn't responding" box (slow emulator) is waved away before anything is tapped.
nowait() { local w; w=$(spot "text:Wait"); [ -n "$w" ] && A shell input tap $w && sleep 1; }
tap() { nowait; local xy; xy=$(spot "$1"); echo "tap $1 -> $xy"; [ -n "$xy" ] && A shell input tap $xy; }

A shell date 110510002026.00; sleep 2
sleep 30; nowait
A shell am start -n $P/.MainActivity; sleep 8; nowait; shot 01-today 1

# 1. Closing today by mistake, then opening it again: the tasks come back.
go tab:0; shot 01b-before-close 5
go closeday; shot 02-today-closed 4
go tab:0; shot 03-ended-card 4
tap "text:افتح اليوم من جديد"; shot 04-reopened 4

# 2. Day planning: add a task from the goals, put it in a period, take it out: it stays out.
go day; shot 05-dayplan 6
tap "text:+ إضافة"; shot 06-add-picker 2
tap "text:بوكسنق"; shot 07-added-to-list 3
tap "text:بوكسنق"; sleep 1; tap "text:العصر"; shot 08-put-in-asr 3
tap "text:بوكسنق"; sleep 1; tap "text:المهام ·"; shot 09-taken-out 2; shot 10-still-out-after-8s 8
tap "text:قراءة كتاب"; shot 10b-routine-tap 2

# 3. At 00:30 the day before is still going: not closed by itself, and closing closes it (not the new date).
A shell date 110600302026.00; sleep 2
go tab:0; shot 11-night-0030 6
go closeday; shot 12-closed-at-night 3
A shell date 110510002026.00; sleep 2

# 3b. A yearly plan from zero.
go yeargoal:p:quran-read; shot 13-year-sheet 5
nowait; for k in 1 2 3 4 5; do A shell input swipe 540 1900 540 700 300; sleep 1; done
tap "text:خطّطه من جديد"; shot 14-reset-ask 2
tap "text:ألغِ القديم"; shot 15-reset-editor 4

# 4. Instagram's answer from inside its page (no account on the emulator).
go igtest; shot 16-ig-answer 20; shot 16b-ig-answer 25

# 5. Swipes on a real row.
go tab:0; sleep 6
Y=$(A shell uiautomator dump /sdcard/u.xml >/dev/null; A pull /sdcard/u.xml /tmp/u.xml >/dev/null; python3 - <<'PY'
import re
x=open('/tmp/u.xml',encoding='utf-8').read()
ys=[(int(a)+int(b))//2 for a,b in re.findall(r'text="روتين"[^>]*bounds="\[\d+,(\d+)\]\[\d+,(\d+)\]"',x)]
print(ys[0] if ys else 1900)
PY
)
(A shell input swipe 250 $Y 700 $Y 3000 &) ; sleep 1.8; A exec-out screencap -p > shots/17-half-right.png; sleep 4
go tab:0; sleep 6
(A shell input swipe 850 $Y 400 $Y 3000 &) ; sleep 1.8; A exec-out screencap -p > shots/18-half-left.png; sleep 4
A logcat -d -t 600 > shots/logcat.txt
ls -la shots
