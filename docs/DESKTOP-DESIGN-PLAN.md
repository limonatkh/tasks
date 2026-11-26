# Desktop design: plan (not approved yet)

Status 2026-11-26: four mockups drawn on the canvas «رحلة اليوم – اللابتوب» (https://claude.ai/artifact/8wZdsrHHGVvcYFMj1jA4Jn, private to the user); source copied in `design-board/desktop/`. Waiting for approval.
No mockup drawn yet. Build the boards from `DESIGN.md` and `design-board/` (copy the inline SVG of «نور», domain tiles and nav icons from boards 1, 3, 4).

## Screens to mock up (audit)

| Board | Content | States |
|---|---|---|
| Today (1440 wide) | right rail: drawn nav (اليوم / التخطيط / الأهداف / المراجعة); sky header with the day arc full width; centre: «هلأ» card + periods with task rows; left column: the arc card (now / picked moment) | normal, night (sleeping «نور»), empty day |
| Week | 7 day columns (Saturday first, right to left), period lanes per day, tasks dragged by mouse between days and periods; drag ghost + drop highlight | normal, dragging, over-full day (warm orange, never red) |
| Task card | side panel instead of a bottom sheet: drawing, name, goal link, «ابدأ جلسة» (the one orange button) + «✓ خلصت», tiles نقل / بكرا / الوقت / تعديل | open, done moment |
| Goals | violet level band with «نور», domain levels, real achievements, year view beside it | normal, level-up dialog |
| Sync / sign-in | Google sign-in, sync state chip in the header | signed out, syncing, offline |

## Desktop-only rules

- Mouse: hover states, right-click menu on a task, keyboard shortcuts (N = new task, Space = done).
- Phone-only features: alarms → Windows notifications; Instagram / YouTube open in the browser; the Quran link stays on the phone.
- Same palette, fonts (Baloo Bhaijaan 2, IBM Plex Sans Arabic), drawing and motion rules as the phone.
