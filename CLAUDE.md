# Smart Life Planner («رحلة اليوم»)

Read before starting any work: `docs/HANDOFF.md`, `docs/DESIGN.md`, `docs/design-board/README.md`.
They hold the decisions, the state, the design tokens and the open questions. This file is only the short version.

## Talking to the user

- Answer in Palestinian Arabic, short, without technical terms. Code, comments and commit messages in English.
- Explain your understanding before large work and wait for approval. Ask only for decisions that can't be undone.
- After every stage: an honest report of what was really tested and what was not.

## Hard rules

- «ما تعدل اشي فتخرب اشي»: the phone app is used every day. Don't change behaviour that works.
- «افتح محاكاة قبل ما تبعتلي اشي»: no APK or build goes to the user before an emulator check.
- New versions install over the old one (same package `com.imad.planner`, signed by `scripts/sign.sh` in CI with key rotation). Never ask the user to uninstall.
- Room DB `planner-v2.db` (version 6): any schema change needs a migration; data must survive updates.
- No secrets in the repo (it is public for now). No passwords, keys or tokens in commits.
- Design: no red for status, one lantern-orange main button per screen, every number from real saved data, no fake rewards, no copied BUFF art. Calm motion (`calmMotion()`) must turn all motion off.
- Arabic RTL everywhere; account names and links stay LTR (bidi isolates).
- Instagram: no Reels/Explore/home feed, only chosen accounts, never save the password.

## Workflow

- Work on a branch; merge to `main` only when the stage works. A push to `main` publishes a release (`build-N`) to the phone.
- CI (`.github/workflows/build.yml`) runs `gradle testReleaseUnitTest :shared:test`, then `assembleRelease`, then signs. There is no Gradle wrapper; CI uses Gradle 8.9 and JDK 17.
- Emulator checks run in CI via `.github/workflows/screens.yml` (inputs: a script from `scripts/`, and `old` = the release installed first). `check11.sh` checks an update from the release given in `old` (0.35 → 0.36 used `old=build-77`).
- This laptop: JDK 17 is installed; the Android SDK has only `cmdline-tools` (no platform-tools, no emulator, no Gradle) as of 2026-11-26, and services.gradle.org is not reachable from the sandbox. Build in CI.

## Folders

- `android/`: the phone app (Gradle name still `:app`, so the APK is still `app-release.apk`). Package `com.imad.planner`.
  - `Data.kt`: Room database, migrations, `AndroidPlatform`, `Repository.get(context)`. `PlatformUi.kt`: fonts, the bottom `Sheet`, `systemCalmMotion()`. `MainActivity.kt`: shell, Today, lock.
  - Tests that need Android-side code: `AlarmEventsTest`, `ExtrasTest`.
- `shared/src/main/kotlin`: plain Kotlin (JVM), no Android: models, `Store` (Room DAO interface), `Repository` (all app rules), `Platform` (what Repository needs from a device), scheduler, prayer times, plans, progress, Quran/Instagram parsing. Tests: `gradle :shared:test`.
- `shared/src/compose/kotlin`: the theme and drawings (`Ui.kt`, `Art.kt`, `SportIcons.kt`, `ProgressUi.kt`), used by both apps.
- Both apps compile `shared/` as their own sources (`srcDir`), not as a library: Kotlin won't smart-cast another module's properties.
- `desktop/`: the Windows app (Compose Desktop 1.7). `DesktopStore` (in-memory Store saved to `%APPDATA%\SmartLifePlanner\data.json` via `Repository.backup()/load()`), `DesktopPlatform`, `PlatformUi.kt` (fonts, side-panel `Sheet`), `Main.kt` (window, rail), `Screens.kt` (Today, week with mouse drag, goals, task panel), `Shots.kt` (draws every screen to PNG without a window).
  - CI: `.github/workflows/desktop.yml` on Windows: `:shared:test`, `:desktop:shots`, `:desktop:packageMsi`; on `main` it publishes a pre-release `desktop-N` with the MSI.
- Any change to an entity changes the database: CI compares Room's schema hash with `main` and fails if it changed without a migration.

## Sync (0.37 / computer 1.1.0)

- Firebase project `time-manager0`: Google sign-in (phone), Anonymous sign-in (computer as a device), Firestore `me-west1`, rules in `firestore.rules` (published by the user 2026-11-27).
- Cloud data: `users/<owner uid>/rows/<urlencoded "table|id">` = {table, id, data (row JSON or null = deleted), at, device}; `users/<owner>.devices` = paired computers; `pair/<code>` = pairing notes.
- Shared: `Rows.kt` (every field of every table), `Sync.kt` (`TrackingStore`, `Pending`, `SyncEngine`, last change wins, device-only settings in `Rows.localMeta`). Tests: `SyncTest`.
- Phone: `PhoneSync.kt` (Credential Manager + Firebase Auth, `FirestoreCloud`, loop every 30 s while visible), Settings → التزامن مع الكمبيوتر. Prefs file `sync` (not in backup).
- Computer: `DesktopSync.kt` (REST: anonymous sign-up, Firestore commit/runQuery, pairing code), `DesktopSyncUi.kt`, prefs `%APPDATA%\SmartLifePlanner\sync.json`. Pairing replaces the computer's data with the account's.
- `android/google-services.json` is not in git; CI writes it from the `GOOGLE_SERVICES_JSON` secret (set by the user). The computer gets `firebase.properties` from it at build time.
- Not tested yet (needs the user's real devices): Google sign-in on the phone, pairing, real round trips. Emulator and CI cannot sign in.

## Next work

- The user tries sign-in and pairing; fix what shows up.
- Computer: more of the phone's editors (rules/routine, goals, plans), Quran/Instagram stay phone-only.
