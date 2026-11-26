# Design board: «لوحة هوية رحلة اليوم»

The approved art direction of the app, copied from the claude.ai design artifact of the same name (the original link is
private to the user). Build new screens, desktop included, **on these**, with the rules and exact values in
[`../DESIGN.md`](../DESIGN.md).

Each board is here twice:
- `.html`: the source, with inline SVG drawings and exact colours, sizes and text. It opens in a browser and uses the
  app's own fonts from `app/src/main/res/font/`.
- `.png`: a picture of it (2× scale), to look at.

| File | What it shows | Size |
|---|---|---|
| `1-mascot-and-logo` | Three mascot ideas. **Chosen: أ «نور» the lantern** (its states and the app mark). ب and ج were not chosen. | 1280×900 |
| `2-colour-type-drawing-rules` | Palette with roles and contrast, the prayer-period colours, the type scale, the four drawing rules | 1280×900 |
| `3-domain-cards-nav-icons-empty-state` | Illustrated domain cards (Quran, sport, content), the four drawn nav icons, the empty state with sleeping «نور» | 1280×900 |
| `4-phone-today` | Today screen (phone) | 390×844 |
| `5-phone-task-card-and-done` | Task card bottom sheet, and the moment a task is done | 390×844 |
| `6-phone-goals-progress` | Goals: level band, domain levels, goal road, real achievements | 390×844 |
| `7-motion-table` | Every moment of motion: trigger, what moves, duration, end, interrupted / calm | 1280×844 |
| `0-older-written-proposal.html` | The written proposal made **before** the board (audit, screen ideas, points rules). **Superseded where it differs:** its palette (e.g. lantern `#EF7A2E`, violet `#7A5CE0`, isha `#4F6D7A`) and some ideas (settings in six sections, a week card on the review page, level names like "مثابر") were not adopted or not built. Use it for the reasoning only. | page |

## Notes

- **The app is the final word.** Where the built app differs from a board, the app is what was approved later. For example, the level names in the app are شمعة / قنديل / فانوس / سراج / منارة / نجم الصبح.
- **A known flaw on board 3:** on the sport card, the subtitle «عقلة · تعليق · كتلبل» overlaps the pull-up bar. Don't copy that.
- **Desktop:** no board exists for it yet. Make desktop mockups in this same language and get the user's approval before building them.
