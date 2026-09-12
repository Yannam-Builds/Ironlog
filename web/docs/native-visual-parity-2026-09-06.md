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
