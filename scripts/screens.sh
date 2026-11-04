#!/bin/bash
# Screens of the app on the emulator, for checking the layout by eye.
set -x
mkdir -p shots
P=com.imad.planner
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
adb root; sleep 3
adb shell settings put secure immersive_mode_confirmations confirmed
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
# The IFrame API's own demo video, which allows embedding.
adb shell am start -S -n $P/.PlayerActivity --es series test2 --es title فيديو --es list "v:M7lc1UVf-VE" --ei index 0 --ei pos 10
shot 14-player-embeddable 8
adb shell dumpsys activity activities | grep -i "resumed" > shots/resumed.txt
adb logcat -d -t 300 > shots/logcat-player.txt
shot 14b-player-embeddable 6
adb shell input tap 540 1200; shot 15-player-embeddable-controls 1
adb shell input keyevent BACK
# Half a swipe on a task row each way, to see the words under it.
adb shell am start -S -n $P/.MainActivity; sleep 6
(adb shell input swipe 250 1850 900 1850 4000 &) ; sleep 2.2; adb exec-out screencap -p > shots/12-swipe-right.png; sleep 4
(adb shell input swipe 900 1850 250 1850 4000 &) ; sleep 2.2; adb exec-out screencap -p > shots/13-swipe-left.png; sleep 4
adb shell dumpsys webviewupdate | grep -i "current webview" > shots/webview.txt
adb logcat -d -t 400 > shots/logcat.txt
