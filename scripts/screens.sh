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
adb emu rotate; shot 10-player-landscape 6
adb shell input tap 1200 540; shot 11-player-landscape-controls 1
adb emu rotate; adb emu rotate; adb emu rotate; sleep 2
adb shell input keyevent BACK
# Half a swipe on a task row each way, to see the words under it.
adb shell am start -S -n $P/.MainActivity; sleep 6
adb shell input motionevent DOWN 300 1512; for x in 380 460 540 620; do adb shell input motionevent MOVE $x 1512; done
shot 12-swipe-right 1; adb shell input motionevent MOVE 300 1512; adb shell input motionevent UP 300 1512; sleep 2
adb shell input motionevent DOWN 780 1512; for x in 700 620 540 460; do adb shell input motionevent MOVE $x 1512; done
shot 13-swipe-left 1; adb shell input motionevent MOVE 780 1512; adb shell input motionevent UP 780 1512
adb logcat -d -t 400 > shots/logcat.txt
ls -la shots
