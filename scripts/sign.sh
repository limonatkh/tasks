#!/bin/bash
# Signs the release APK with the private key, carrying proof that the old (now public) key handed over to it
# (APK signature scheme v3 key rotation). Phones with the app already installed take it as a normal update;
# after that, an APK signed only with the old key is refused as an update.
#   needs: PLANNER_KS_PASS (secret) — opens signing/planner-2026.p12.enc and the key inside it.
#   in:  android/build/outputs/apk/release/app-release-unsigned.apk
#   out: android/build/outputs/apk/release/app-release.apk   (+ attack.apk when MAKE_ATTACK=1, for the emulator check)
set -euo pipefail
cd "$(dirname "$0")/.."
OUT=android/build/outputs/apk/release
BT="$ANDROID_HOME/build-tools/$(ls "$ANDROID_HOME/build-tools" | sort -V | tail -1)"
W=$(mktemp -d); trap 'rm -rf "$W"' EXIT
if [ -z "${PLANNER_KS_PASS:-}" ]; then
  echo "::error::PLANNER_KS_PASS is not set: add it under Settings → Secrets and variables → Actions. Nothing was signed."
  exit 1
fi
openssl enc -d -aes-256-cbc -pbkdf2 -iter 300000 -in signing/planner-2026.p12.enc -out "$W/new.p12" -pass env:PLANNER_KS_PASS
"$BT/zipalign" -p -f 4 "$OUT/app-release-unsigned.apk" "$W/aligned.apk"
# Proof of rotation: old key → new key. (The old one must stay able to read the app's data: "installed data" on.)
"$BT/apksigner" rotate --out "$W/lineage" \
  --old-signer --ks signing/old-public.keystore --ks-pass pass:planner123 --ks-key-alias planner \
  --new-signer --ks "$W/new.p12" --ks-pass env:PLANNER_KS_PASS --ks-key-alias planner2
"$BT/apksigner" sign --rotation-min-sdk-version 28 \
  --ks signing/old-public.keystore --ks-pass pass:planner123 --ks-key-alias planner \
  --next-signer --ks "$W/new.p12" --ks-pass env:PLANNER_KS_PASS --ks-key-alias planner2 \
  --lineage "$W/lineage" --out "$OUT/app-release.apk" "$W/aligned.apk"
"$BT/apksigner" verify -v --print-certs "$OUT/app-release.apk" | grep -E "Verified using|Signer|SHA-256" || true
if [ "${MAKE_ATTACK:-0}" = 1 ]; then
  # What someone holding only the old public key could make: the same app signed by it alone.
  "$BT/apksigner" sign --ks signing/old-public.keystore --ks-pass pass:planner123 --ks-key-alias planner --out attack.apk "$W/aligned.apk"
fi
