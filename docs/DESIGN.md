# Design system: «رحلة اليوم» (Smart Life Planner)

The approved look of the app (approved by the user in the claude.ai session; see `HANDOFF.md` §2). The values below
are the ones in the code (`Ui.kt`, `Art.kt`). The design board itself is a claude.ai artifact named
«لوحة هوية رحلة اليوم» and is copied into [`design-board/`](design-board/README.md) as HTML (source) and PNG (pictures): build on those.

**Desktop: no design exists yet.** Nothing was drawn or decided for the laptop screens. It must be designed, from these
rules, and shown to the user for approval before it is built.

---

## 1. How design work is done here (the user's design skill)

These rules come from the user's skill `illustrated-interactive-app-design`. They were followed for this app and apply to any new screen,
including desktop.

- **Quality bar:** the visual and interactive quality of BUFF Workout, with an **original** identity.
  - Never copy BUFF's logo, lettering, characters, illustrations or layouts.
- **Order of work:**
  1. **Audit:** list every screen and state.
  2. **Art direction:** mascot ideas, palette with contrast, illustration rules, type, illustrated cards, nav icons, one functional screen and one progress screen.
  3. **Real visual mockups.**
  4. **The user's approval.** No code before it.
  5. **Implementation.**
  6. **Testing:** on the emulator, before anything is sent.
- **Design DNA:**
  - **Drawn identity first:** a mascot, app mark, drawn nav icons, domain art, empty states, badges. Standard icons only where clarity needs them.
  - **Bright but restful:** a white or very light canvas; strong colour only on what is in focus (the header, the one main button, rewards). Not every card saturated.
  - **Illustrated cards** with a composition (drawing, title, shape, colour pair), not a label on a flat box.
  - **Working screens stay simple:** text, spacing and controls come before decoration.
  - **Playful only when it means something:** motion on press, select, add, done, level-up and open. No constant animated noise, no fake rewards, never delay input.
  - **Every screen in the same language:** dialogs, sheets, empty, loading and error states included.
- **Progress honesty:**
  - Every number comes from real saved data.
  - Levels follow a real model: points from real minutes, done tasks, goal quarters and records (see `Progress.kt`).
  - No streak guilt: "kept weeks" replace streaks.
- **Be honest about limits:**
  - Hand-coded vector drawings and Compose animation are what can be made here.
  - A full mascot turnaround and rigged animation (Lottie/Rive) need an illustrator or animator.
  - Say so; never pretend such assets exist.
- **Review rubric (0–5 each), judged from visual evidence:**
  - original identity
  - consistency
  - colour comfort and contrast
  - card craft
  - clarity of the main flows
  - in-context actions
  - motion
  - accessibility and RTL
  - performance
  - nothing lost

  Anything not seen is marked NOT TESTED.

---

## 2. Identity

- **Mascot: «نور», a lantern.** Chosen from three concepts. The other two were «شمسة وهلال» (sun and crescent) and «بذرة» (a seed).
- **Moods** (`NourMood`):
  - `Dim`: not lit
  - `Awake`: lit, normal
  - `Happy`: open smile
  - `Cheer`: happy eyes and stars
  - `Sleep`: eyes closed, "z z"
- **App icon:** «نور» on a sky-blue tile (`res/drawable/ic_launcher_fg.xml`, background `#2F56D9`).
- **Screen titles:** "اليوم", "التخطيط", "الأهداف", "المراجعة".

---

## 3. Colour (`Brand` in `Ui.kt`)

| Role | Name | Light | Text on it | Contrast |
|---|---|---|---|---|
| Header, chosen tab | سما (sky) | `#2F56D9` | white | 6.1:1 |
| The one main button | فانوس (lantern) | `#FF8A3D` | ink `#1C2340` | 6.6:1 |
| Rewards (stars, medals) | نجمة (star) | `#FFD25E` | ink | 10.7:1 |
| Levels, goal roads | بنفسجي (violet) | `#5A3DB8` | white | 7.5:1 |
| Done (only "done") | نعناع (mint) | `#137A53` | white | 5.3:1 |
| Calm containers, fields | سما فاتحة | `#EAF0FF` | ink | 13.5:1 |

**Neutrals**

| Role | Value |
|---|---|
| Paper | `#FFFFFF` |
| Background | `#F4F7FE` |
| Ink | `#1C2340` |
| Muted ink | `#5F6787` (5.2:1 on the background) |
| Warm words like «هلأ» and «شغّالة» | `#B85A1E` (`warmText()`); in dark mode `#FFB27A` |

- Never write yellow text on white.
- Never use red for status. Red is kept only for errors and delete.

**Dark mode** (`journeyColors(dark = true)`)

| Role | Value |
|---|---|
| Background | `#141833` |
| Surface | `#1E2347` |
| Text | `#ECEFFA` |
| Muted text | `#A9B0CC` |
| Header | `#26389A` |
| Primary | `#9DB4FF` |
| Done | `#4FC48F`, with ink text on it |

**Black and white** (`monoColors`): the same layout in greys. Drawings turn grey (`Color.tone(mono)` in `Art.kt`).

**Prayer periods:** colour shows only on the dot, the title and the thin lane; never as a full background, except a light tint behind the current period.

| Period | Colour |
|---|---|
| الفجر | `#5B6BC0` |
| الظهر | `#E0A030` |
| العصر | `#DB7A3C` |
| المغرب | `#C0587A` |
| العشاء | `#3B4A8C` |

**Domains** (`Domain` enum): each has a colour and a pale tile.

| Domain | Colour | Tile |
|---|---|---|
| القرآن (Quran) | `#0F8C7E` | `#D8F2EE` |
| القراءة (reading) | `#2F56D9` | `#DDE8FF` |
| الرياضة (sport) | `#DB6A2C` | `#FFE6D6` |
| المحتوى (content) | `#5A3DB8` | `#EDE7FF` |
| الشغل (work) | `#B07A10` | `#FFF0C2` |
| أخرى (other) | `#C0587A` | `#F9E1EA` |

In dark mode a tile is the domain colour at 28% over the surface.

**User tag colours** (saved by their index, so keep the order): `#FF8A3D`, `#2FA36B`, `#2F56D9`, `#E0A030`, `#7457D6`, `#0F8C7E`, `#C0587A`, `#8A90A8`.

---

## 4. Type

- **Fonts:**
  - **Display:** Baloo Bhaijaan 2, Bold and ExtraBold (`DisplayFont`), for titles and big numbers.
  - **Body:** IBM Plex Sans Arabic, Regular, SemiBold and Bold (`BodyFont`), for everything people read.
  - Both are under the OFL licence (`assets/licenses/fonts-OFL.txt`).
- **Scale (sp):**

  | Use | Size |
  |---|---|
  | Screen title | 30 ExtraBold |
  | `titleLarge` | 25 |
  | Card title | 22–23 |
  | `titleMedium` | 19 |
  | Task name | 16.5 SemiBold |
  | Body | 17 / line height 27 |
  | Secondary | 15 |
  | Note | 13 |

- Text must stay readable at font scale 1.3 (checked).

---

## 5. Shape and layout

- **Corners:**
  - extraSmall 10, small 14, medium 20, large 26, extraLarge 30 dp
  - buttons 16
  - task rows 18
  - sheets 30 at the top
  - the header band 30 at the bottom
- **Sizes:** main button at least 52 dp tall (secondary 48); touch targets about 48 dp; page gutter 16 dp.
- **Page header** (`PageHeader`): a sky band with a big white title, the date under it, and square round buttons (`HeaderButton`, 46 dp, white at 16%). Today also shows the day arc.
- **Bottom nav** (`JourneyNav`): 4 drawn icons. The chosen one is coloured, with a light pill behind it.
- **Sheets** (`Sheet`): all sheets come up from the bottom. A handle with × beside it, height capped at 94% of the screen.
- **Segmented control** (`Segmented`): a white piece slides to the chosen option. The first option is on the right (RTL).
- **RTL:**
  - everything mirrors;
  - account names and links stay left-to-right (`⁦…⁩` or `TextDirection.Content`);
  - the arc reads from the right: morning on the right, night on the left.

---

## 6. Drawing rules (every drawing, `Art.kt`)

- **Outline:** ink `#1C2340`, about 1/32 of the drawing's width, round joins and ends. In practice:

  | Drawing | Size | Outline |
  |---|---|---|
  | icon | 28 | 2.5 |
  | card | 120 | 4 |
  | character | 160 | 5–6 |

- **Shading:** three tones per material: the base colour, one darker side (about 12% darker), and a small white highlight. No gradients and no soft shadows.
- **Shapes:** round and full, big heads, oval ink eyes with a light dot, pink cheeks (`#F4A0A0`). No small details that disappear at 28 dp.
- **Colour:** at most two colours besides ink in one drawing, and only from the palette.
- **Method:** drawn on Canvas in a design box (`art(vw, vh)`) and scaled, so drawings stay sharp at any size.
  - Design boxes: «نور» 200×200 (its body 160×180 starts at (20, 10)), domain art 40×40, nav icons 40×40, medal 44×48.
- **Current drawings:**
  - «نور», all five moods
  - the six domain drawings: mushaf on a stand, book, kettlebell, microphone, laptop, note with a tick
  - the four nav icons: sun on the horizon, calendar, mountain with a flag, bars
  - the day arc
  - the medal
  - the goal road
  - the sparkle star

---

## 7. Motion (`Art.kt`, `Ui.kt`)

**Rules**

- No motion delays a tap.
- Every celebration can be skipped.
- Nothing moves on its own except «نور» on the arc.
- `calmMotion()` is true when the phone's animator scale is 0 or the in-app «حركة أقل» is on. Then everything goes to its end state at once.

| Moment | From | What moves | Time / curve | To | If interrupted / calm |
|---|---|---|---|---|---|
| Press a card or button | 100% | sinks to 96% and back | 120 ms, soft spring | 100% | back at once / colour only |
| Task marked done | empty circle | circle fills mint, tick draws itself; «+N» and two stars rise and fade | 300 ms fill; cheer 450 ms rise + 350 ms hold + 300 ms fade | task struck through | undo row stays (duration غير مؤكد) / tick without stars |
| Swipe a task | in place | follows the finger; behind it «ابدأ الآن» (lantern) or «نقل» (sky) | with the finger, back in 200 ms | moved or started | let go before half = back |
| Open a sheet | below the screen | slides up, the page dims | 260 ms ease | bottom sheet | back / × / tap outside / calm: appears |
| Segmented switch | old option | white piece slides | 200 ms | new option | — / instant |
| Bottom tab | grey icon | icon colours, pops 0.86→1, pill opens | 200 ms pill, spring | chosen | — / colour only |
| Tab content | old page | crossfade | 220 ms | new page | — / instant |
| «نور» idle | — | light breathes; blink every ~4.6 s | 2.4 s breath, 150 ms blink | — | still when calm |
| «نور» on the arc, picked | at "now" | walks along the arc to the picked moment; card expands below | 350 ms walk, 240 ms card | the picked moment | returns after 8 s, or on × or a second tap |
| Tap on «نور» | — | hop up 10 dp and back | 140 ms up, spring down | — | none when calm |
| New level | full bar | «نور» cheers with stars, level number large | dialog; hop on open | level card | shown once per level; closes at once |
| Period expand | arrow ▾ | arrow turns 180° | 200 ms | open/closed | instant when calm |

**What would make motion better** (not done): a Lottie or Rive file of «نور» made by an animator.

---

## 8. Screens as built (phone)

- **Today:**
  - header with the date, the level chip (★ + level) and the day arc;
  - the «هلأ» card: domain tile, title, «ابدأ», «مش هلأ», the pomodoro button;
  - the prayer-table warning on a star tint (not red);
  - the «بالمواقيت / كل المهام» switch and «بلا روتين»;
  - period titles: the current one is a warm card, the others plain lines;
  - task rows: domain tile, name, quiet line of notes, done circle;
  - when the day is over: sleeping «نور».
- **Day-arc card:**
  - **for now:** done/total, real time, points today, level bar, the next or running task with «ابدأ»;
  - **for a picked moment:** its period, how far from now, its tasks (each opens its card), free time, «+ مهمة بال…».
- **Task card** (bottom sheet): drawing, name, line, link to its goal, «ابدأ جلسة» + «✓ خلصت», four tiles (نقل / بكرا / الوقت / تعديل), extra chips, «إعدادات المهمة ▼».
- **Goals:** violet level band with «نور», domain levels, «إنجازات حقيقية» (2 earned + 2 next), the goal buttons, then the year view. The progress sheet adds goal roads and all badges.
- **Planning:** «بكرا / الأسبوع / الشهر» segmented control.
- **Lock:** sky background, white card with «نور» (Dim when the password is wrong).
- **Instagram** (`IgFocusActivity`): sky header «إنستغرام الهادي», «حساباتي / الرسائل», account chips with avatars, story circles with a lantern ring, photo post cards, a full-screen black story viewer.

---

## 9. Desktop: what is known

- **Nothing is designed yet.**
- The only direction given:
  - wide layouts;
  - the planning screens should show the whole week at once, with tasks dragged by mouse;
  - same palette, fonts, drawings and motion rules as the phone.
- **Before building:** make real mockups (at least Today, a week view, a task card, goals) and get the user's approval.
