#!/bin/bash
# 0.34 new signing key: 0.33 (old key) installed with some data → 0.34 installs over it as an update and the data
# is still there → an APK signed only with the old (public) key is refused as an update.
mkdir -p shots
exec > >(tee shots/run.log) 2>&1
set -x
P=com.imad.planner
A() { timeout 60 adb "$@"; }
A root; sleep 3
A shell pm disable-user --user 0 com.google.android.apps.nexuslauncher || true
spot() { A shell uiautomator dump /sdcard/u.xml >/dev/null; A pull /sdcard/u.xml /tmp/u.xml >/dev/null; python3 scripts/find.py /tmp/u.xml "$1"; }
tap() { local xy; xy=$(spot "$1"); echo "tap $1 -> $xy"; [ -n "$xy" ] && A shell input tap $xy; }
shot() { sleep "${2:-4}"; A exec-out screencap -p > "shots/$1.png"; }
ls -la old.apk attack.apk android/build/outputs/apk/release/
A install old.apk | tee shots/install-old.txt
A shell dumpsys package $P | grep -E "versionName|signatures" | tee shots/old-package.txt
A shell pm grant $P android.permission.POST_NOTIFICATIONS
A shell am start -n $P/.MainActivity; sleep 6; A shell am force-stop $P
printf '<?xml version="1.0" encoding="utf-8" standalone="yes" ?>\n<map>\n    <boolean name="unlocked" value="true" />\n</map>\n' > /tmp/lock.xml
A push /tmp/lock.xml /data/data/$P/shared_prefs/lock.xml
U=$(A shell stat -c %u /data/data/$P | tr -d '\r'); A shell chown $U:$U /data/data/$P/shared_prefs/lock.xml
A shell am start -n $P/.MainActivity; shot 01-old-today 8
tap "desc:علّم"; shot 02-old-one-done 3
A shell "sqlite3 /data/data/$P/databases/*.db \"select count(*), sum(state='done') from tasks; select count(*) from meta;\"" | tee shots/db-old.txt || true
A shell am force-stop $P
# The update.
A install -r android/build/outputs/apk/release/app-release.apk 2>&1 | tee shots/install-new.txt
A shell dumpsys package $P | grep -E "versionName|signatures|pastSigs|signing" | tee shots/new-package.txt
A shell pm grant $P android.permission.POST_NOTIFICATIONS
A shell "sqlite3 /data/data/$P/databases/*.db \"select count(*), sum(state='done') from tasks; select count(*) from meta;\"" | tee shots/db-new.txt || true
A shell am start -n $P/.MainActivity; shot 03-new-today-kept 8
A shell am start -S -n $P/.MainActivity --es open tab:2; shot 04-new-goals 6
# The old public key alone must not be able to replace it.
A install -r attack.apk 2>&1 | tee shots/install-attack.txt
A shell dumpsys package $P | grep -E "versionName" | tee shots/after-attack.txt
A logcat -d -b crash > shots/crash.txt
