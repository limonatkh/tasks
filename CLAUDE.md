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
- CI (`.github/workflows/build.yml`) runs `gradle testReleaseUnitTest`, then `assembleRelease`, then signs. There is no Gradle wrapper; CI uses Gradle 8.9 and JDK 17.
- Emulator checks run in CI via `.github/workflows/screens.yml` (input: a script from `scripts/`).
- This laptop: JDK 17 is installed; the Android SDK has only `cmdline-tools` (no platform-tools, no emulator, no Gradle) as of 2026-11-26.

## Code map (`app/src/main/java/com/imad/planner/`)

- Logic without Android imports (candidates for a shared module): `Scheduler`, `PlanMath`, `Progress`, `Prayer`, `Targets`, `Extras`, `Projection`, `PlansJson`.
- `Data.kt`: Room entities, DAO, `Repository`, `Settings`. `Ui.kt` / `Art.kt`: theme tokens and all drawings. `MainActivity.kt`: shell, Today, lock.
- Tests: `app/src/test/java/com/imad/planner/` (must stay green).

## Next work (see HANDOFF §7)

The laptop (Windows) version that does everything the phone does and syncs with it. Still open: Windows app vs website, Firebase project, sync conflict rule, and the desktop design (must be mocked up and approved before any code).
