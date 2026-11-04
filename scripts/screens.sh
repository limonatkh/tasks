#!/bin/bash
# Screens of the app on the emulator, for checking the layout by eye.
set -x
mkdir -p shots
P=com.imad.planner
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
adb root; sleep 3
shot() { sleep "${2:-4}"; adb exec-out screencap -p > "shots/$1.png"; }
go() { adb shell am start -S -n $P/.MainActivity --es open "$1"; }
adb shell am start -n $P/.MainActivity; shot 01-today 8
adb shell input keyevent BACK; sleep 1
for t in 0 1 2 3; do go "tab:$t"; shot "02-tab$t"; done
go watch; shot 03-watch-sheet
go goal; shot 04-new-goal
go month; shot 05-month
adb shell input swipe 540 1900 540 600 400; shot 05b-month-scroll 2
go week; shot 06-week
go ig; shot 07-ig-feed
# A public playlist in the full-screen player.
adb shell am start -n $P/.PlayerActivity --es series test --es title "تجربة" --es list PLlrxD0HtieHhS8VzuMCfQD4uJ9yne1mE6 --ei index 0 --ei pos 30
shot 08-player 20
adb shell input tap 540 1200; shot 09-player-controls 1
adb shell settings put system accelerometer_rotation 0
adb shell settings put system user_rotation 1; shot 10-player-landscape 6
adb shell input tap 1200 540; shot 11-player-landscape-controls 1
adb shell settings put system user_rotation 0
adb logcat -d -t 400 > shots/logcat.txt
ls -la shots
