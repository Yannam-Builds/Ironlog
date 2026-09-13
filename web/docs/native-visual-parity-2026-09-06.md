# Native visual parity handoff — 6 September 2026

The Android Compose app in `Z:\KOTLIN\UnifiedPort` is the visual and behavioral source of truth. This pass compared the live release app on the API 36.1 emulator with the Vite app at a 390 × 844 CSS-pixel viewport. No Android source, release package, owner data, or deployment was changed.

## Completed slice

| Surface | Native authority | Web implementation | Result |
| --- | --- | --- | --- |
| Shared shell | `navigation/IronLogTabBar.kt`, `navigation/AppNavigator.kt` | `src/App.tsx`, `src/styles.css` | Primary tabs no longer show the web-only brand bar. The tab bar is a 64px floating glass capsule inset 16px with native icon order, labels, and a sliding-style selected pill. Detail routes use a compact native-style back/title bar. |
| Home | `ui/screens/home/HomeScreen.kt` | `src/features/Home.tsx` | Native order, copy, spacing, proof card, plan action, 30-day session metrics, weekly goal, plan-day chips, weekly summary, recovery, Ledger and recent work. Metrics use the existing credited-proof and tracking engines. |
| Plans | `ui/screens/plans/PlansScreen.kt` | `src/features/Plans.tsx` | Native Training/Plans/count header, three full-width actions, list empty state and New Plan action. Create with AI opens a local goal/day plan builder backed by the current native template catalog. |
| Log | `ui/screens/stats/HistoryScreen.kt` | `src/features/Progress.tsx` | Native Workout Log/History/count header, search, date filter, progress-photo destination, empty state and Go to Today action. Existing history editing remains intact. |
| Stats | `ui/screens/stats/StatsScreen.kt` | `src/features/Progress.tsx` | Native Analytics header, destination tiles, four metrics, Status Window, performance tabs and 14-day frequency. Existing PR content continues below. |
| Settings | `ui/screens/settings/SettingsScreen.kt` | `src/features/Settings.tsx` | Native Training Console header, local-record summary, search and destination card. Existing browser-capable controls remain below and destination rows scroll to them. |

## Visual evidence

- Android captures: `Z:\KOTLIN\UnifiedPort\artifacts\implementation-2026-09-05\native-*-parity.png`.
- Browser captures: `output/playwright/native-parity-*.png`, including `native-parity-home-mobile.png` at 390 × 844.
- The screenshots use synthetic/local emulator data. Theme colors follow each installation's saved theme; structure and semantic roles remain shared across themes.

## Verification

- `npm run check` — passed.
- `npm test -- --run` — 24 files, 138 tests passed.
- `npm run build` — production bundle and service worker generated successfully.
- `npm run verify:output` — 110 generated files verified.
- `npm run test:e2e` — 49/49 passed across Chromium, WebKit and the configured Android-sized Chromium subset on 12 September 2026.
- The 200%-text WebKit pass exposed a non-shrinking Settings summary badge. The badge now wraps inside its card and the complete matrix passes.

## Platform adaptations that remain explicit

Android-only operating-system surfaces cannot be literal browser copies: Health Connect, home-screen widgets, foreground-service alarms, Android notification channels, wallpaper Monet, and device camera URIs. The web keeps the corresponding data and workflow destinations where browsers provide an equivalent, and describes unavailable OS guarantees without fabricating them.

Future device acceptance should compare active-workout overlays and each Settings destination on physical Android and iPhone hardware. Automated route coverage already exercises 320px layouts, 200% text, focus containment, offline startup, multi-tab writes and complete workout journeys.

## 12 September 2026 — native assets, navigation and motion

- `npm run sync:native` now exports every one of the 68 density-independent PNG artwork files, the two native splash wordmarks and all 21 native font files from `app/src/main/res`. Badge art keeps its browser path, and the generated provenance manifest records a SHA-256 hash for every source file. A unit contract fails if either native inventory drifts from the web export.
- The five bottom-navigation icons now use the exact AndroidX Compose vector paths selected by `AppNavigator.kt`: outlined Home, Fitness Center, Assignment, Show Chart and Settings. Their rendered size is the native 21 dp, and the Log/Stats auto-mirroring behavior is retained for RTL layouts.
- The tab bar uses the native 64 dp bar, 5 dp inset and 54 dp moving selection pill. Its travel and tint timings approximate the Compose springs while remaining CSS-only and honoring `prefers-reduced-motion`.
- Web bottom sheets, floating workout resume control, transient messages, onboarding-step content, progress bars, segmented controls and active rest-timer digits now reproduce the corresponding Compose entrance, selection and rolling-digit motion.
- First launch in a browser tab uses the native split IRON/LOG wordmarks, 52%-viewport sizing, opposing 700 ms entrances, impact shake and staggered loading dots. Repeat route loads in the same tab skip the splash so browser reload recovery remains immediate.
- Switch settings use the native 52 × 32 Material geometry and IronLog theme roles for checked/unchecked thumb, track and border colors. Multi-select and confirmation controls remain checkboxes and now use the app palette instead of the browser-default artwork.

Compose controls and animations are executable Kotlin rather than image assets. They are implemented with semantic HTML and CSS so keyboard, screen-reader and reduced-motion behavior remains valid. Android-only operating-system surfaces listed above remain the only intentional platform exclusions.

## 12 September 2026 — interaction and motion parity follow-up

- The Home “Today’s workout” card now uses the native `animatedCardShine` recipe: the same 3% / 20% / 28% / 4% accent stops, a 900 px diagonal gradient, linear travel from -600 px to 1800 px over five seconds, and reverse playback for the ten-second full cycle.
- Saved plans now expose the native 44 px drag handle and can be reordered with mouse, touch, or pen. Cards lift while held and surrounding cards interpolate into their new positions over 220 ms. Dropping a plan into the first position also makes it the active plan, matching the requested top-plan behavior. Arrow Up and Arrow Down on the focused handle provide the same operation for keyboard users.
- Primary route content now enters with a short pager-style horizontal transition. Newly logged set rows, expanded detail content, active-plan badges, and the Ledger XP bar reproduce the visible Compose fade, slide, scale, and 800 ms progress motions.
- The five primary tabs now behave as an ordered pager: a horizontal swipe advances exactly one tab, works in both directions, follows the pointer before settling, resists movement past Home and Settings, and yields to vertical scrolling and nested horizontal lists. Form fields and sliders keep direct gesture ownership, and completed swipes suppress compatibility clicks so controls underneath the release point do not activate.
- Every continuous or entrance animation remains disabled by the existing `prefers-reduced-motion` rule. Motion parity tests cover pointer reordering, keyboard reordering, top-plan activation, and the Home shine attachment.

## 13 September 2026 — active workout parity

- The active workout header now follows the Compose card structure with a rolling elapsed-time pill, explicit minimize action, and live volume comparison against the latest completed session with the same name.
- Exercise cards use native-style drag handles. Mouse, touch, and pen dragging lifts the held card and animates surrounding cards into place; Arrow Up and Arrow Down provide the same persistent reorder for keyboard users.
- Logged sets now expose the Compose set label, tappable W/WU/DS/F/AMRAP type badge, and inline RPE/RIR chip. Set types cycle in the same native order and continue through the serialized IndexedDB mutation boundary.
- The rest banner now has a circular progress treatment, rolling digits, last-set context, and a completion pulse. Browsers with vibration support receive short confirmation patterns for logging, reordering, and completion.
- The finish sheet now previews duration, work-set count, and total volume with the native completion mark and a short theme-colored burst. The app still commits only after the explicit save action.
- The native volume card and fun load comparisons are present below the exercise list. All new motion inherits the global reduced-motion override.

Automated coverage now verifies persistent keyboard and pointer exercise reordering, set-type cycling, completion-summary rendering, full workout persistence, and the existing reload/rest/warmup journey.
