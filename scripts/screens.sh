#!/bin/bash
# Screens of the app on the emulator, for checking the layout by eye.
set -x
mkdir -p shots
P=com.imad.planner
adb install -r app/build/outputs/apk/release/app-release.apk
adb shell pm grant $P android.permission.POST_NOTIFICATIONS || true
adb root; sleep 3
# Past the one-time password screen.
adb shell am start -n $P/.MainActivity; sleep 5; adb shell am force-stop $P
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <boolean name="unlocked" value="true" />\n</map>\n' > /tmp/lock.xml
adb push /tmp/lock.xml /data/data/$P/shared_prefs/lock.xml
U=$(adb shell stat -c %u /data/data/$P | tr -d '\r'); adb shell chown $U:$U /data/data/$P/shared_prefs/lock.xml; adb shell chmod 660 /data/data/$P/shared_prefs/lock.xml
adb shell settings put secure immersive_mode_confirmations confirmed
shot() { sleep "${2:-4}"; timeout 20 adb exec-out screencap -p > "shots/$1.png"; }
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
# Does the WebView draw at all in the player screen (red page + a plain HTML5 video)?
adb shell am start -S -n $P/.PlayerActivity --es series t0 --es title test --es list x --ez test true
shot 00-webview-test 12
adb shell input tap 540 1200; shot 00b-webview-test-controls 1
adb emu rotate; shot 00c-webview-test-landscape 4
adb emu rotate; adb emu rotate; adb emu rotate; sleep 2
adb shell input keyevent BACK
# Instagram's login page on its own screen.
adb shell am start -n $P/.IgLoginActivity; shot 16-ig-login 15
adb shell input keyevent BACK; adb shell input keyevent BACK
# A public playlist in the full-screen player.
adb shell am start -n $P/.PlayerActivity --es series test --es title "تجربة" --es list PLlrxD0HtieHhS8VzuMCfQD4uJ9yne1mE6 --ei index 4 --ei pos 60
shot 08-player 20
adb shell input tap 540 1200; shot 09-player-controls 1
adb emu rotate; shot 10-player-landscape 6
adb shell input tap 1200 540; shot 11-player-landscape-controls 1
adb emu rotate; adb emu rotate; adb emu rotate; sleep 2
adb shell input keyevent BACK
# The IFrame API's own demo video, which allows embedding.
adb shell am start -S -n $P/.PlayerActivity --es series test2 --es title فيديو --es list "v:M7lc1UVf-VE" --ei index 0 --ei pos 10
shot 14-player-embeddable 12
adb shell dumpsys activity activities | grep -i "resumed" > shots/resumed.txt
adb logcat -d -t 300 > shots/logcat-player.txt
shot 14b-player-embeddable 8
adb shell input tap 540 1200; shot 15-player-embeddable-controls 1
sleep 6; shot 15b-player-controls-hidden 1
adb shell input keyevent BACK
# Half a swipe on a task row each way, to see the words under it.
adb shell am start -S -n $P/.MainActivity; sleep 6
# Half swipes (held mid-way) show the colour and word; full swipes show what really happened.
(adb shell input swipe 250 1610 800 1610 4000 &) ; sleep 2.2; adb exec-out screencap -p > shots/12-half-right.png; sleep 4
(adb shell input swipe 850 1610 300 1610 4000 &) ; sleep 2.2; adb exec-out screencap -p > shots/13-half-left.png; sleep 4
adb shell input swipe 150 1610 1000 1610 250; shot 17-full-right-result 3
adb shell am start -S -n $P/.MainActivity; sleep 6
adb shell input swipe 1000 1850 100 1850 250; shot 18-full-left-result 2
adb shell dumpsys webviewupdate | grep -i "current webview" > shots/webview.txt
adb logcat -d -t 400 > shots/logcat.txt
