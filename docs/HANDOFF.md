# Handoff: Smart Life Planner (state on 2026-11-26)

Written at the end of a long claude.ai session, for the next agent working on this repository.
Everything here comes from that session and from the code at commit `354a43a`. Where a fact was not
checked, it says **(غير مؤكد)** (not confirmed). This file holds no passwords and no keys.

Design rules, tokens, drawing rules and motion timings are in `docs/DESIGN.md`.

The user writes Palestinian Arabic. Answer in Palestinian Arabic. Code, comments and commit messages are in English.

---

## 1. Goal

A personal Android planner (`com.imad.planner`, the user's Samsung Galaxy M13) that arranges each day around the prayer times, tracks goals from the day up to the year, and has a calm, illustrated look with honest progress.
Next: a laptop (Windows) version that does everything the phone does and stays in sync with it.

---

## 2. Final decisions

User wording is quoted exactly, in Arabic, where the user gave it.

### Process

| Decision | Reason |
|---|---|
| Check every change on the emulator before sending an APK. User: «افتح محاكاة قبل ما تبعتلي اشي» | The user has received broken builds before. |
| Don't break what already works. User: «ما تعدل اشي فتخرب اشي» | Features are relied on every day. |
| Explain your understanding before big work; wait for approval when a change is large. | User preference, said many times. |
| Redesign approvals. User: «١ايا شغلة. ٢ تمام. ٣آه كل الحالات. ٤ ممكن ٥دفعة وحدة. بس اذا بتقظر تعمل التحريكات بكون مائة المائة، حاول شوف كيف طريقة ممكن تقدر تعملها» | The answers, in order: mascot = any, so «نور» the lantern was picked; palette approved; level names in all places; level star on the Today header; everything in one batch; push the animations as far as possible. |
| No red on the planner. "Needs a look" is warm orange (`Brand.lanternText` / `warmText()`). | The user said the old screen with "تعليم أحمر" was not comfortable to look at. |
| Today must show all of the day's tasks one tap away, without clock times, and the prayer periods must be clearly told apart. User: «بدي بضغطة مباشرة، في الوجه أشوف كل مهام اليوم قدامي بدون التوقيت الزمني… وتحت في التوقيت تاع الصلاة، كلهن خاشات في بعض، ميزهن بأي طريقة…» | Built as the «بالمواقيت / كل المهام» switch, plus a colour per period. |

### Today screen and day arc

| Decision | Reason |
|---|---|
| The day arc reacts to touch. User: «بدي الشاشة اللي فوق تكون تفاعلية اكثر، اقدر اضغط عالفانوس يعطيني بيانات مهمة، بطريقة نفس الستايل طبعاً، واقدر اضغط عأي مكان في الخط يتوجه الفانوس اله مؤقتاً ويعطيني بيانات عنه، او يعطيني بيانات بدون ما يتوجه، ايا حاجة المهمة خليها تفاعلية بقدر المستطاع.» | Implemented in 0.31. |
| The lantern must not be cut off at the top of the arc. User: «الفانوس هان مقصوصة راسه بتصير لما يكون فوق، في خط فوق بغطيه» | Fixed in 0.33. |

### Instagram

| Decision | Reason |
|---|---|
| The password is never stored. User: «ما بدي يحفظ كلمة السر» | Sign-in happens on Instagram's own page. The app keeps only the WebView session cookie. |
| Show posts and messages only, no Reels. User: «…عمل في تطبيق تجربة استخدام انستقرام مخصصة، بيعرض منشورات بدون ريل، ورسائل فقط، شوفلي الموضوع وحله.» | Instagram's own site runs inside a WebView, filtered. |
| Stories and posts come only from accounts the user adds. User: «ما بدي ستوري ومنشورات الكل، بدي ستوري ومنشورات مخصص بخسابات بضيفها، اعطيني طريقة اني اخصص التجربة بحسابات احددها، مش كل الفلورز اللي عندي، مكان اضيف فيه رابط الحساب او اسمه وينضاف.» | This is the «حساباتي» tab. Instagram's home feed is closed. |

### Quran app link (the other developer's app)

| Decision | Reason |
|---|---|
| User: «انا معرف الصفحة انها انقرت انه اكون فتت عالصفحة اللي بعدها. واغلاق التطبيق يعني طلعت منه، وضله شغال بالخلفية» | A page counts as read when the reader moves forward to the next page. Leaving the app = `onStop`, even if it keeps running in the background. Written into `QURAN_INTEGRATION_V2.md`. |

### Planning and series

| Decision | Reason |
|---|---|
| Re-planning from scratch replaces the old plan, for all tasks and goals. User: «١ لما اخططها من جديد يعني لغيت القديم خلص، برجع من البداية. ٢ آه كل المهام والأهداف» | |
| A missed series item moves forward; it is not skipped. User: «السلسة تتزحزح، لانه مفروض اليوم انجزر شغلة معينة، مثلاً في الفرآءة، وما قريتهن، والمرة الجاية مش حعمل سكيب عاللي ما قريته، بل حقرأ اللي ما قريته.» | |

### App lock

| Decision | Reason |
|---|---|
| Password lock before the app opens, asked once ever. User: «ضيفلي خيار كلمة مرور قبل فتح التطبيق… كلمة مرور بتنطلب مرة وحدة بالعمر» (the password itself is left out here on purpose) | Only a SHA-256 hash is in the code (`LOCK_HASH`, `MainActivity.kt`). Once unlocked, `shared_prefs/lock.xml` holds `unlocked=true`. |

### Signing and repository

| Decision | Reason |
|---|---|
| Release key rotated (0.34). Releases are signed with a private key using APK Signature Scheme v3 rotation (old → new). | The old keystore and its password were public in this repository. Rotation lets installed copies update in place without data loss, and stops the old key from signing updates. |
| The repository is public for now. User: «انا بس بدي اعدل هالفترة عالبرنامج، وبعد بشوي حعمل المستودع خاص لما ارجع اعدل عليه من الابتوب» | Free Actions minutes while the user edits from the phone. It will go private later. |
| No licence was added. | All rights stay with the user. |
| Personal data left in the repo: the Gaza prayer timetable, project names, the Quran partner's package name. | Judged low risk; the prayer table is a working part of the app. |

### Laptop version

The user's answers to "Windows? / Google sign-in? / laptop only plans or does everything? / all at once?":
«١ آه. ٢ آه. ٣ لكل اشي، نفس الجوال. ٤ مرة وحدة كله.»

| Decision | Reason |
|---|---|
| Proposed: a Windows app built from the same Kotlin code (Compose Multiplatform: a shared module plus Android and Desktop/JVM targets), synced through Firebase (Firestore + Google sign-in), last write wins per row. **The user has not confirmed "Windows app instead of website" (غير مؤكد).** | "Does everything like the phone" means a website would need the whole logic rewritten twice. |

---

## 3. Product rules

### Must

- Arabic, right-to-left everywhere.
  - Account names and links read left to right: wrap them in `⁦…⁩`, or use `TextDirection.Content`.
- One main (lantern-orange) button per screen.
- Points come only from the real saved history. No points are bought or handed out for nothing.
- A level-up celebration happens once per level. The first time a version runs, the level already reached is recorded silently: `levelSeen`.
- Every celebration can be skipped. Nothing moves unless the user acts, except «نور» on the arc.
- "حركة أقل" (in-app) and the phone's animator scale 0 both turn motion off.
- Points can be switched off. This hides them and deletes nothing.
- The backup holds every meta row except undo.
- Instagram:
  - no Reels, no Explore, no general home feed;
  - only the accounts the user chose;
  - no password saving (`saveFormData = false`, autofill off).
- Install new versions over the old one. Never ask the user to uninstall: data would be lost.

### Must not

- No red for status.
- No fake rewards or invented statistics.
- No copied BUFF Workout art, logos or characters. The visual style is original.
- No secrets in the repository.
- No APK sent without an emulator check.

---

## 4. Technical details

### Build

| Item | Value |
|---|---|
| Kotlin, Compose material3 | BOM `2024.09.03` |
| Room | `2.6.1` (KSP), DB file `planner-v2.db`, version 6, migrations `MIGRATION_1_2` … `MIGRATION_5_6` |
| Coil | `2.7.0` |
| SDK levels | `minSdk 26`, `targetSdk 35`, `compileSdk 35` |
| Current version | `versionCode 35`, `versionName "0.34"` (also shown in Settings: `Muted("الإصدار 0.34")` in `Editors.kt`) |
| No local Android SDK | in the session that wrote this file; all builds ran on GitHub Actions |
| Fonts | `res/font/`: `baloo_bold.ttf`, `baloo_extrabold.ttf` (static instances made from the Baloo Bhaijaan 2 variable font), `plex_regular/semibold/bold.ttf` (IBM Plex Sans Arabic); licence in `assets/licenses/fonts-OFL.txt` |

### CI

- **`.github/workflows/build.yml`** (every push):
  1. `gradle testReleaseUnitTest`
  2. `assembleRelease` (unsigned)
  3. `bash scripts/sign.sh` with secret `PLANNER_KS_PASS`
  4. On `main`, publishes the release `build-N` with `Smart-Life-Planner.apk`.
- **`.github/workflows/screens.yml`** (manual, input `script`):
  1. builds and signs, with `MAKE_ATTACK=1`
  2. downloads release `build-68` (0.33, old key) as `old.apk`
  3. runs `scripts/<script>` on an API 34 `pixel_6` emulator
  4. publishes release `screens-N` with `screens.zip`
- **Downloading release assets:** use `gh api -H "Accept: application/octet-stream" repos/limonatkh/tasks/releases/assets/<id>`. (`gh release list` sometimes missed the newest `screens-*`; filter `repos/.../releases?per_page=40` instead.)

### Signing (`scripts/sign.sh`)

- **Key files:**
  - New key: `signing/planner-2026.p12.enc`, encrypted with `openssl enc -aes-256-cbc -pbkdf2 -iter 300000`. The passphrase is secret `PLANNER_KS_PASS`; the same value opens the PKCS12 store. Alias `planner2`, RSA 4096, DN `CN=Smart Life Planner`.
  - Old, public key: `signing/old-public.keystore`, alias `planner`. It is kept only to sign the rotation lineage; its password is in `sign.sh`.
- **Steps:**
  1. `zipalign -p 4`
  2. `apksigner rotate` (old → new)
  3. `apksigner sign --rotation-min-sdk-version 28 --ks old … --next-signer --ks new … --lineage`
  4. Output: `app/build/outputs/apk/release/app-release.apk`. With `MAKE_ATTACK=1` it also writes `attack.apk`, signed with the old key only.
- **New cert SHA-256:** `eaf78fdc6146f734c7b9bf026dc473684693ff527653201355a4d4c84087ce99` (needed for Firebase / Google sign-in on Android).
- **Old cert SHA-256:** `52:AE:AB:54:D0:AD:BB:FF:A8:3C:3D:BD:A9:2B:6A:76:31:2E:02:B4:D4:FA:88:3A:9B:CE:76:AC:A1:DD:63:67`.
- **If `PLANNER_KS_PASS` is missing**, `sign.sh` fails with an `::error::` and nothing is released.
- **Repository secrets:**
  - `PLANNER_KS_PASS`: confirmed by a successful signed build on 2026-11-22.
  - `FIREBASE_SA` was asked for; **whether it was added is غير مؤكد**.

### Source files (`app/src/main/java/com/imad/planner/`)

**Data and logic**

- **`Data.kt` (≈2500 lines):** entities, DAO (`store`), `Repository.get(context)`, `Settings` (`Settings.KEYS`, `Settings.from(map)`), `Maths` (`spent`, `remaining`, `over`).
  - Entities: `Task`, `Rule`, `Event`, `Goal`, `GoalLog`, `Project`, `Meta`.
  - Backup: `export` writes `store.allMeta()` minus `undo`, `undoLabel`, `undoAt`. Restore puts every meta key back.
- **`Scheduler.kt`:** fits tasks into periods. Items have `readyAt`, `dueByHours`, `strict`; sorted by priority first.
- **`Progress.kt`:** pure functions, no Compose.
  - Constants: `TASK = 10`, `MILESTONE = 50`, `RECORD = 30`.
  - `pointsOf(task)`:
    - finished with the timer: 10 + real minutes, capped at `ceil(1.5 × estimate)`;
    - finished without the timer: 10 + `estimate × pct / 100`;
    - open: real minutes only (same cap);
    - skipped: 0.
  - Levels: `need(n, step=50) = step·(n−1)·n`, so level 2 = 100 points, level 3 = 300. Domain levels use `step=25`.
  - Level names: 1 شمعة, 3 قنديل, 5 فانوس, 8 سراج, 12 منارة, 17 نجم الصبح.
  - Kept week (`committed`): at least 5 tasks in the week and ≥70% done. Only finished weeks count; the week starts on Saturday (`PlanMath.startOfWeek`).
  - Achievements (`Badge`): task counts (1, 10, 50, 100, 250, 500, 1000), domain hours (5, 10, 25, 50, 100, 250, 500), kept weeks (1, 4, 12, 26), goal quarters, personal records from `GoalLog`.
  - `earned()` lists newest first; `next()` sorts by `near` (how close).
- **Other logic files:** `PlanMath.kt`, `Prayer.kt`, `Targets.kt`, `Extras.kt` (`Estimates.suggest`), `QuranLink.kt`, `QuranAudio.kt`, `Alarms.kt`, `Projection.kt`, `PlansJson.kt`.

**Theme and shared UI**

- **`Ui.kt`:**
  - `Brand` colour tokens.
  - `journeyColors(dark)` and `monoColors(dark)`; `PlannerTheme(mono)`.
  - Font families `DisplayFont` (Baloo) and `BodyFont` (Plex).
  - Composable colour helpers: `headerColor()`, `ctaColor()`, `onCtaColor()`, `doneColor()`, `warmText()`.
  - `ThemeChoice(mono, set, calm, setCalm)` + `LocalThemeChoice`.
  - `calmMotion()`, `Modifier.pressScale()` (96%, spring).
  - `Action` / `Secondary` buttons.
  - `Sheet(title, close, content)`: every sheet is a bottom sheet sliding up in 260 ms, height capped at 94%.
- **`Art.kt`:** all drawings, on Canvas, in design boxes:
  - `Nour(mood, size, cheer, label)`. Moods: `NourMood.Dim / Awake / Happy / Cheer / Sleep`. Glow breathes over 2.4 s, a blink every ~4.6 s, a hop on `cheer`.
  - `Domain` enum: `Quran`, `Reading`, `Sport`, `Content`, `Work`, `Other`. `domainOf(category, icon)` / `domainOf(task, settings)`. `DomainTile(domain, size, look, done)`.
  - `JourneyNav` (4 drawn tabs), `PageHeader`, `HeaderButton`.
  - `DayArc(periods, minute, line, card)` + `ArcPick(minute, now)` + private `ArcGeo` (quadratic curve: `p1 = (w/2, −0.24h)`, box 126 dp tall).
  - `Medal`, `DoneCircle` (300 ms fill and tick).
  - `Cheer.fire(points, title)` + `CheerLayer`.
  - `NourNote`, `Segmented`.
- **`ProgressUi.kt`:** `LevelChip`, `Bar`, `ProgressBand`, `DomainLevels`, `GoalRoad`, `BadgeTile`, `progressItems()` (LazyList items for the goals page), `ProgressSheet`, `LevelUpDialog`, `ar(n)` (Arabic-Indic digits).

**Screens**

- **`MainActivity.kt`:**
  - **Lock:** `LockScreen`.
  - **Shell:** the Scaffold with `PageHeader` (+ `DayArc` on Today) and `JourneyNav`. Tabs: اليوم / التخطيط / الأهداف / المراجعة.
  - **Today:** `TodayTab`, `NextPanel` (the «هلأ» card), `PeriodTitle`, `TaskRow(…, onDone)` with `DoneCircle`, `ViewSwitch`, `ArcCard` (the card under the arc).
  - **Progress wiring:** summary, `levelSeen` check, `cheerFor`.
  - **Debug routes** (`am start … --es open <route>`): `month`, `week`, `tab:N`, `watch`, `ig`, `goal`, `menu`, `postpone`, `askclose`, `prayers`, `rule48`, `closeday`, `reopen`, `yeargoal:<g:id|p:id>`, `igtest`, `day`, plus extra `startQuran`.
- **`Reports.kt`:**
  - `TaskMenu(t, close, ops, yt, domain, look, goal)`: header tile, link to its goal, «ابدأ جلسة» and «✓ خلصت», four `QuickTile`s (نقل / بكرا / الوقت / تعديل), extra chips, «إعدادات المهمة ▼».
  - `GoalLink(name, line, open)`.
  - `SwipeRow`: in RTL, swipe to start = start the task (lantern colour); the other way = move it (sky colour).
- **`PlanScreens.kt`:**
  - `PlanningTab`: `Segmented` بكرا / الأسبوع / الشهر.
  - `GoalsTab(s, nav, sum, openProgress)`.
  - `YearView(s, nav, top)`: `top` injects the progress items.
- **Other screens:** `PlanningUi.kt`, `Editors.kt` (settings: «حركة أقل» = ui pref `calm`; «النقاط والمستويات» = meta `xp`, `"0"` = off), `PrayerUi.kt`, `SportsUi.kt`, `Charts.kt`, `YtAndParts.kt`, `PlayerActivity.kt` (YouTube), `Widget.kt`, `StageEditors.kt`, `SportIcons.kt`.

**Instagram**

- **`IgFocusActivity.kt`:** tabs «حساباتي» | «الرسائل».
  - **The WebView** stays alive underneath. It loads `https://www.instagram.com/direct/inbox/` when signed in, otherwise `https://www.instagram.com/`. It runs the JS below and exposes the bridge `PlannerFocus` (`stopped()`, `got(kind, user, code, body)`).
  - **`FOCUS_JS`:**
    - hides links to `/reels`, `/explore`;
    - stops clicks on reel/explore links and `/reel/`;
    - after `pushState` / `replaceState` / `popstate`, a guard sends closed paths (and the home feed when signed in) to `/direct/inbox/` with `location.replace`;
    - every 1 s, removes empty `div[role="dialog"]` windows that have stayed more than 3 s, and resets `overflow`.
  - **`blocked(path)`:** `/`, `/reels`, `/reel/…`, `/explore`, `/<user>/reels`.
  - **`fetchJs(users)`:** from inside the page, with `credentials: 'include'`, header `X-IG-App-ID: Ig.APP_ID`:
    1. `/api/v1/users/web_profile_info/?username=<u>` per account, 700 ms apart;
    2. then `/api/v1/feed/reels_media/?reel_ids=<id>&reel_ids=<id>…`;
    3. each result goes to `PlannerFocus.got('p' | 's' | 'end', …)`.
  - **Parsing:**
    - profile: `data.user.{id, profile_pic_url_hd, full_name, edge_owner_to_timeline_media.edges[].node}`; skips `is_video` and `product_type == "clips"`; sidecars use only their image children.
    - stories: `reels{}` or `reels_media[]` → `items[].image_versions2.candidates[0].url`, `video_versions[0].url`, `taken_at`.
  - **Accounts** are stored in meta `igPeople2` (`Ig.people` / `Ig.encodePeople`, `IgPerson(user, addedAt)`). Input is parsed with `Ig.username(text)` and accepts a link, `@name` or a name.
  - **Story viewer:** pictures stay 5 s, videos use a `VideoView`. Tap the left half = next, right half = previous.
  - **Intent extra `url`:** an Instagram address to open; blocked addresses are refused (used by the checks).
- **`IgUi.kt`:** `Ig` helpers (`UA`, `APP_ID`, `loggedIn()` = cookie has `sessionid=`, `loginAllowed(url)`, `why(code)`, `username`, `parsePosts`). Also holds the **old, now unused** `IgFeedScreen` and `IgWeb.fetchAll`.
- **`IgLoginActivity.kt`:** the older full-screen login (غير مؤكد whether still reachable).

### Storage outside Room (not in the backup)

SharedPreferences, phone only:
- `ui`: `mono`, `calm`, the Today view choice.
- `lock`: `unlocked`.

### Meta keys seen in code (partial)

`plans`, `months`, `targets`, `routine`, `routineWeeks`, `weeklyRoutine`, `colors`, `icons`, `groups`, `groupPeriods`, `places`, `quranLinks`, `quranState`, `quranSeen`, `quranSyncAt`, `quran:*`, `ytLinks`, `yearParts`, `pausedGoals`, `estSkip`, `safety`, `igPeople2`, `igFeed`, `igFetchedAt`, `pomo`, `pomo:*`, `closed:*`, `dayPlanned:*`, `planning:*`, `dayMode`, `seed-v2` … `seed-v7`, `undo`, `undoLabel`, `undoAt`, `xp`, `levelSeen`.

### Tests (`app/src/test/java/com/imad/planner/`)

`AlarmEventsTest`, `DayShapeTest`, `ExtrasTest`, `PlanMathTest`, `PrayerTest`, `ProgressTest`, `ReviewTest`, `SchedulerTest`, `TargetsTest`, `V5Test`, `V7Test`.

### Emulator scripts (`scripts/`)

- **Shared helpers:** `find.py <dump.xml> <mode>` with modes `text:`, `desc:`, `chip-right`, `after:` (prints tap coordinates). `arcpoints.py <png>` finds «نور», the maghrib station and a point on the arc.
- **Every script:**
  - disables the Pixel launcher (it hung and blocked uiautomator), so a stray Back leaves the app on a "Pixel is starting" screen: open screens by route instead;
  - grants `POST_NOTIFICATIONS`;
  - pushes `lock.xml` to skip the lock;
  - writes `shots/run.log`.
- **What each covers:**
  - `check7.sh`: full visual pass of 0.30 (light, dark, black and white, night, large text).
  - `check8.sh`: the arc interactions and the Instagram screen.
  - `check9.sh`: «حساباتي» signed out.
  - `check10.sh`: 0.33 → 0.34 update, database before and after, attack APK refused.

### Design board (outside the repo)

claude.ai artifact «لوحة هوية رحلة اليوم», boards Main / Palette / Cards / Today / Sheet / Progress / Motion. Its contents are the source of the palette, illustration rules and motion timings used in `Ui.kt` and `Art.kt`.

---

## 5. Status

### Done and checked on the emulator

- **0.30 redesign:**
  - theme, fonts, «نور», domain tiles, nav, day arc, task row with done tick and cheer, task card sheet, goals page with level band, domains, achievements, `ProgressSheet`, `LevelUpDialog`;
  - lock screen, settings switches, launcher icon;
  - checked in dark mode, black and white, night (sleeping «نور») and at font scale 1.3.
- **0.31:**
  - arc: tapping «نور», a station or the line, dragging, and the 8 s return all work;
  - Instagram: refuses `/explore/` (back to start, counter «وقّفت 1»).
- **0.32:** «حساباتي» signed out:
  - accounts added by link and by `@name`;
  - a bad input shows an error;
  - the list is kept after a restart.
- **0.33:** the lantern head is complete at the top of the arc.
- **0.34:**
  - installs over 0.33 as an update;
  - task and meta counts match before and after (4 tasks / 10 meta);
  - the old-key APK is refused with `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
- **Unit tests:** all pass in CI, including `ProgressTest`.

### Written but not checked

- Instagram signed in:
  - «حساباتي» stories and posts;
  - the messages tab after sign-in;
  - the stuck-dialog remover.

  The emulator cannot sign in: Instagram blocks datacenter IPs and there is no test account.
- Frame-by-frame motion: the emulator's screen recording kept too few frames. Start and end states were seen; the motion itself was checked only in code.
- The backup now holds all meta. Export and import were not run end to end on the emulator.
- The Quran app sessions (V2 agreement): they need the partner's app.

### Not started

- The laptop / Windows version and Firebase sync:
  - no shared module;
  - no Firebase code;
  - no Project ID received.

---

## 6. Known problems and what was tried

### YouTube

- **Inside the app, the picture is grey or black on the user's phone. Unresolved.**
  - Tried so far:
    - the embed as the top-level page (an iframe drew nothing);
    - JS `playVideoAt` (the embed `index` parameter was ignored);
    - CSS hiding YouTube's own controls (they caused "two screens");
    - removing `LAYER_TYPE_HARDWARE` (it froze the emulator);
    - an `m.youtube.com` fallback.
- **It can't be tested on the emulator:** errors 150/152 and a "not a bot" wall, because of the datacenter IP.

### Instagram

- **Old feed:** `IgWeb.fetchAll` was blocked while signed out (404 / "not-logged-in"). It was replaced by `IgFocusActivity`.
- **Stuck grey window:** the user's phone showed a grey overlay with an empty loading window.
  - Cause (likely, غير مؤكد): the earlier `pushState` blocking left an Instagram dialog half-open.
  - Fix: navigation is no longer swallowed; a guard redirects instead, and empty dialogs are removed. Not checked signed in.
- **Sign-in address:** `https://www.instagram.com/accounts/login/` gave an HTTP error on the emulator, so sign-in now starts from `https://www.instagram.com/`.
- **Account names:** in an RTL field they showed mirrored ("nasa@"); fixed with bidi isolates.

### Emulator quirks

- **Setting the clock:** `date MMDDhhmm` gave unexpected times (asked for 13:50, the screen showed 01:00).
- **The done-tick tap** (`desc:علّم`) sometimes finds nothing, depending on the time and what is visible.
- **If notifications are not granted,** their dialog covers the screenshots.

### Earlier

- **RTL swipe:** the label and the action did not match. Fixed: `EndToStart` = start; the label is taken from the swipe sign.
- **Closing the day after midnight** closed the wrong date. Fixed with a cutoff based on `dayStart`.

### Still open

- The seeded sport goals have English names (e.g. "Barbell Curl").
- The phone-only prefs (`ui`, `lock`) are not in the backup.
- The old key stays in the repository on purpose: it is needed to sign the lineage.

---

## 7. Next steps, in order

1. Ask the user to confirm "Windows app instead of website" for the laptop (still unconfirmed, see §2).
2. Ask whether the Firebase project exists. If yes, get the Project ID. Check whether the `FIREBASE_SA` secret was added (غير مؤكد). The setup the user was asked to do:
   - create the project (Analytics optional);
   - Authentication → Google;
   - Firestore → production mode;
   - Project settings → Service accounts → new private key, saved as the `FIREBASE_SA` secret.
3. Register the Android app in Firebase with the **new** cert SHA-256 (§4).
4. Move platform-free logic into a shared Kotlin module, without changing behaviour; the unit tests must stay green:
   - models, `Scheduler`, `PlanMath`, `Progress`, `Prayer`, `Targets`, settings parsing;
   - then screens wherever they don't need Android APIs.
5. Sync layer:
   - per-row documents under the user's uid, last write wins, tombstones for deletes;
   - works offline and pushes when back online;
   - security rules: only the owner reads or writes.
   - Plan the first upload of the existing phone data.
6. Desktop target (Compose Desktop, Windows installer built in CI):
   - wide layouts, mouse drag in the planning screens;
   - notifications instead of alarms;
   - Instagram and YouTube open in the browser;
   - the Quran link stays phone-only.
7. Check that the phone still updates over 0.34 with data kept, then emulator screenshots of both targets before sending anything.
8. Later, when the user asks: make the repository private. Note that a private repo has 2000 free Actions minutes per month (about 15 min per emulator check).

---

## 8. Open questions

- Laptop: a Windows desktop app (proposed) or a website? Not confirmed.
- Firebase: has the project been created? Project ID? Was `FIREBASE_SA` added?
- Is the laptop only Windows, or should it run on other systems too (غير مؤكد)?
- On the laptop, which phone-only features need a substitute (alarms, the Quran app link, the YouTube player, the Instagram screen)?
- Sync conflicts: is "last write wins" acceptable to the user (proposed, not discussed)?
- YouTube grey picture on the phone: still wanted inside the app, or open in YouTube?
- Should the phone-only prefs (`ui`, `lock`) join the backup or the sync?
- Is `IgLoginActivity` still needed, or can it and the old `IgFeedScreen` / `IgWeb.fetchAll` be removed (غير مؤكد)?
- Does the Quran partner know the repository is public, and is the V2 session agreement implemented on their side (غير مؤكد)?
