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
shot() { sleep "${2:-4}"; A exec-out screencap -p > "shots/$1.png"; }
go() { A shell am start -S -n $P/.MainActivity --es open "$1"; }
spot() { A shell uiautomator dump /sdcard/u.xml >/dev/null; A pull /sdcard/u.xml /tmp/u.xml >/dev/null; python3 scripts/find.py /tmp/u.xml "$1"; }
tap() { local xy; xy=$(spot "$1"); echo "tap $1 -> $xy"; [ -n "$xy" ] && A shell input tap $xy; }

A shell date 110510002026.00; sleep 2
A shell am start -n $P/.MainActivity; shot 01-today 8

# 1. Day planning: a task taken out of a period stays in the list; "+ إضافة" adds from the goals.
go day; shot 02-dayplan 6
tap chip-right; sleep 1; tap "text:المهام ·"; shot 03-dayplan-moved-out 2; shot 04-dayplan-after-8s 8
tap "text:+ إضافة"; shot 05-add-picker 2
tap "after:كل الأهداف"; shot 06-added 3

# 2. Closing at 00:30 closes the day before, not the new date.
A shell date 110600302026.00; sleep 2
go closeday; shot 07-closed-at-night 4
A shell date 110510002026.00; sleep 2
go tab:0; shot 08-yesterday-closed-today-open 5
# Closing today by mistake, then opening it again.
go closeday; shot 09-today-closed 4
go tab:0; shot 10-ended-card 4
tap "text:افتح اليوم من جديد"; shot 11-reopened 4

# 3. A yearly plan from zero.
go yeargoal:p:quran-read; shot 12-year-sheet 5
A shell input swipe 540 1800 540 500 300; sleep 1; A shell input swipe 540 1800 540 500 300
tap "text:خطّطه من جديد"; shot 13-reset-ask 2
tap "text:ألغِ القديم"; shot 14-reset-editor 4

# 4. Instagram's answer from inside its page (no account on the emulator).
go igtest; shot 15-ig-answer 40

# 5. Swipes on a real row.
go tab:0; sleep 6
Y=$(A shell uiautomator dump /sdcard/u.xml >/dev/null; A pull /sdcard/u.xml /tmp/u.xml >/dev/null; python3 - <<'PY'
import re
x=open('/tmp/u.xml',encoding='utf-8').read()
ys=[(int(a)+int(b))//2 for a,b in re.findall(r'text="روتين"[^>]*bounds="\[\d+,(\d+)\]\[\d+,(\d+)\]"',x)]
print(ys[0] if ys else 1900)
PY
)
(A shell input swipe 250 $Y 700 $Y 3000 &) ; sleep 1.8; A exec-out screencap -p > shots/16-half-right.png; sleep 4
go tab:0; sleep 6
(A shell input swipe 850 $Y 400 $Y 3000 &) ; sleep 1.8; A exec-out screencap -p > shots/17-half-left.png; sleep 4
A logcat -d -t 600 > shots/logcat.txt
ls -la shots
