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
- Emulator checks run in CI via `.github/workflows/screens.yml` (inputs: a script from `scripts/`, and `old` = the release installed first). `check11.sh` with `old=build-74` checks 0.34 → 0.35.
- This laptop: JDK 17 is installed; the Android SDK has only `cmdline-tools` (no platform-tools, no emulator, no Gradle) as of 2026-11-26, and services.gradle.org is not reachable from the sandbox. Build in CI.

## Folders

- `android/`: the phone app (Gradle name still `:app`, so the APK is still `app-release.apk`). Package `com.imad.planner`.
  - `Data.kt`: Room DAO, database, migrations, `Repository`. `Ui.kt` / `Art.kt`: theme tokens, drawings, domain colours. `MainActivity.kt`: shell, Today, lock.
  - Tests that need Android-side code: `AlarmEventsTest`, `ExtrasTest`.
- `shared/`: plain Kotlin (JVM), no Android. Used by the phone now and by the computer app later. Same package.
  - `Models.kt` (entities with Room annotations from `room-common` only, `Settings`, `Groups`, `Clock`, `Maths`, `Cat`, `Months`, `QuranLink`, `num`), `Domains.kt`, `Scheduler`, `PlanMath`, `Progress`, `Prayer`, `Targets`, `Extras`, `Projection`, `PlansJson`.
  - `org.json` is `compileOnly` here (Android has it built in); the computer app must add it.
  - Tests: `gradle :shared:test`.
- `desktop/`: the Windows app (not started).
- Any change to an entity changes the database: CI compares Room's schema hash with `main` and fails if it changed without a migration.

## Next work (see HANDOFF §7)

The laptop (Windows) version that does everything the phone does and syncs with it. Still open: Windows app vs website, Firebase project, sync conflict rule, and the desktop design (must be mocked up and approved before any code).
