#!/bin/bash
# 0.32: «حساباتي» (chosen accounts) and the messages page; not signed in on the emulator, so: the sign-in card,
# adding and removing accounts by link or name, the messages tab showing Instagram's login, closed addresses.
mkdir -p shots
exec > >(tee shots/run.log) 2>&1
set -x
P=com.imad.planner
A() { timeout 25 adb "$@"; }
A root; sleep 3
A shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
spot() { A shell uiautomator dump /sdcard/u.xml >/dev/null; A pull /sdcard/u.xml /tmp/u.xml >/dev/null; python3 scripts/find.py /tmp/u.xml "$1"; }
tap() { local xy; xy=$(spot "$1"); echo "tap $1 -> $xy"; [ -n "$xy" ] && A shell input tap $xy; }
shot() { sleep "${2:-4}"; A exec-out screencap -p > "shots/$1.png"; }
A install -r app/build/outputs/apk/release/app-release.apk
A shell am start -n $P/.IgFocusActivity; shot 01-open-login 12
tap "text:حساباتي"; shot 02-mine-empty 3
tap "text:رابط الحساب أو اسمه"; sleep 1; A shell input text "https://www.instagram.com/natgeo/"; sleep 1; A shell input keyevent 111; sleep 1
tap "text:إضافة"; shot 03-added-one 3
tap "text:رابط الحساب أو اسمه"; sleep 1; A shell input text "@nasa"; A shell input keyevent 111; sleep 1; tap "text:إضافة"; shot 04-added-two 3
tap "text:رابط الحساب أو اسمه"; sleep 1; A shell input text "instagram.com/reels/"; A shell input keyevent 111; sleep 1; tap "text:إضافة"; shot 05-wrong 3
tap "text:تم"; shot 06-done 2
tap "text:الرسائل"; shot 07-messages 10
A shell am force-stop $P
A shell am start -n $P/.IgFocusActivity --es url https://www.instagram.com/explore/; shot 08-explore 10
A shell am force-stop $P
A shell am start -n $P/.IgFocusActivity; shot 09-reopen-kept 10
tap "text:حساباتي"; shot 10-reopen-mine 3
A logcat -d -b crash > shots/crash.txt
