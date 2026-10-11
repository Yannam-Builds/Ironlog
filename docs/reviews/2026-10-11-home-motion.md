# Home and interaction motion — 2026-10-11

Implemented in the isolated IronLogWeb worktree. No native changes, builds, publication, or commits.

## Source trace and delivered behavior

Read native `HomeScreen.kt` StartWorkoutCard (781 onward), ActiveWorkoutTimerText, `ui/CardGradient.kt`, onboarding `InfiniteNumberWheelSheet.kt`, theme type tokens, and WorkoutRepository first-set timer comments. Compared actual signed native `output/android/parity-native-home.png`, `parity-native-resume.png`, and cycle phase frames against browser captures. Native capture: 1080 × 2400, density 2.625, system font scale 1.

- Home now displays the plan title, goal badge, day count/average, 48px round start/resume action, recommended day first, horizontally scrolling day cards that directly start their chosen day, protected training rhythm explanation, and START WITHOUT PLAN.
- Active card displays workout name, IN PROGRESS, running first-logged-set timer (zero before any sets), resume, and discard confirmation. Uses existing transactional start/discard APIs; failure leaves confirmation open. Completed history remains untouched. No-plan card can start an empty session.
- Shine uses raw native physical coordinates (-600 → 1800, end +900), resolved physical card height, and 5s forward +5s reverse linear SVG animation. ResizeObserver maps CSS dimensions through browser DPR into the SVG viewBox. Switch off unmounts the layer; reduced motion removes SMIL animation while retaining its static initial brush.
- NumberWheel uses five visible 48px snapping rows, cyclic scrolling through three repeated ranges with middle-range recentering, native touch scrolling, mouse dragging, and bounded keyboard/direct numeric alternatives. Visual row clicks select their value. Numeric bounds remain explicit in spinbutton semantics.

Ownership: `web/src/features/Home.tsx`, `web/src/ui/NativeCardShine.tsx`, NumberWheel and play/resume icon entries in `web/src/ui/components.tsx`, Home/wheel portions of `web/src/styles.css`, `web/tests/home-motion-closure.test.tsx`, updated native-label/height expectation in `web/tests/motion-parity.test.tsx`.

## Verification

`npm run check` passes. Focused Vitest: Home closure + existing motion + navigation parity, 25 tests pass. New integration checks start a nonrecommended day, cancel/confirm discard without altering history, verify zero before first set, and exercise cyclic scrolling alongside bounded keyboard/numeric entry.

Playwright CLI (no added Playwright specs), isolated `home-motion` session, development port 5182. Ignored browser evidence under `web/output/playwright/`:

- `home-motion-320.png`, `home-motion-411.png`, `home-motion-1280.png`: settled Home; document scrollWidth equals viewport width at each size; start action remains 48px. Card widths 292/371/560px. Carousel clips/scrolls within its card.
- `home-motion-resume.png`, `home-motion-discard-sheet.png`: first-set-free resume timer at 00:00 and confirmation sheet, canceled to retain session.
- `home-shine-phase-00.png` through `home-shine-phase-10.png`, `home-shine-measurements.txt`: DPR2.625 full cycle sampled at each second. x1 measured -600/-120/360/840/1320/1800/1320/840/360/-120/-600; x2 always +900. ViewBox dimensions match measured CSS card ×2.625, with tiny float rounding at reversal. Phase colors are visibly diagonal and return symmetrically; native screenshots are an unsynchronized cycle so exact frame pixel diffs are not claimed.
- `home-shine-reduced.png`, `home-shine-off.png`: runtime assertions find zero animate elements in reduced motion and zero shine layer when disabled.
- `home-number-wheel-320.png`: actual wheel scrolling moves birth year 1995 →1998 after 144px scroll; Home/ArrowUp remain bounded at1900.

Browser contexts copy only local synthetic test data. Native stored fitness data is untouched.

## Limits and remaining concerns

Home timer reconstructs the first logged timestamp rather than persisting a durable timer-start field. Removing the earliest/all logged sets can move/reset its origin. Existing Workout header and completion duration use startedAt; full native first-set duration parity requires a coordinated store/schema/Workout change and is not claimed here.

Native and browser title line wrapping still differ because of platform font/layout metrics; card structure, source sizes, brush geometry, and actionable controls are matched, not claimed pixel-identical. Existing Daily Proof text/mascot differs from the signed native screenshot and was left outside this focused card/motion slice.

Reviewed existing App tab swipe: it ignores horizontally scrollable children, protects inputs, performs threshold/velocity navigation and route animation. Existing plan-list drag animates displaced rows with Web Animations and supports keyboard/persisted ordering. Existing modal Sheet opens as native HTML dialog with focus trap/backdrop dismissal and CSS entry animation; reduced-motion CSS disables animations. Existing motion tests pass, but this slice did not add a real touch tab-swipe or plan drag width matrix; that runtime evidence remains pending. No changes to those already functioning paths.
