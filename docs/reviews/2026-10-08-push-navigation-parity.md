# Push navigation parity — 2026-10-08

## Native ownership evidence

Source: `app/src/main/java/com/ironlog/app/navigation/AppNavigator.kt` in this worktree. `NavHost` registers `Tabs` at line 232. The five-page `Tabs` implementation starts at line 819. Its floating active-workout pill is rendered at lines 989–1007 and its tab bar at lines 1009 onward. Consequently neither control belongs to root destinations pushed above `Tabs`. Root screens use `popBackStack()` rather than recreating a tab destination. The web now follows that ownership for its dedicated detail routes.

| Web route | Native counterpart / evidence | Shell owner | Direct-entry Back fallback |
| --- | --- | --- | --- |
| `home` | Tabs/Home | Tabs | — |
| `plans` | Tabs/Plans | Tabs | — |
| `log` | Tabs/Log | Tabs | — |
| `stats` | Tabs/Stats | Tabs | — |
| `settings` | Tabs/Settings | Tabs | — |
| `workout` | ActiveWorkout variants, lines 243–316 | Root | Home |
| `plan/:id` | PlanEditor, lines 317–334 | Root | Plans |
| `library` | ExerciseLibrary, lines 335–345 | Root; feature owns header | Settings |
| `exercise/:identity` | ExerciseProgress, lines 359–364; opened from Stats and Library | Root | Stats |
| `body` | BodyWeight, lines 365–395 | Root | Stats |
| `calendar` | WorkoutCalendar, lines 396–404; opened from Stats | Root | Stats |
| `history/new[/:date]` | HistoricalWorkout, lines 405–413 | Root | Log |
| `measurements` | BodyMeasurements, lines 414–435 | Root | Stats |
| `analytics` | VolumeAnalytics, lines 436–441 | Root | Stats |
| `recovery` | RecoveryMap, lines 442–447 | Root | Home |
| `intelligence` | TrainingIntelligence, lines 448–460 | Root | Home |
| `photos` | ProgressPhotos, line 476; opened from Log and Stats | Root | Stats |
| `ledger` | statusWindow, lines 590 onward; opened from Home and Stats | Root | Home |
| `history/:id` | Native HistoryCard expands inside HistoryScreen; web has a dedicated detail page | Web root adaptation | Log |
| `research` | Web-specific research page | Web root adaptation | Home |

Actual recorded origins override these direct-entry fallbacks. For example, Library → Exercise Progress returns to Library; a photo route opened from Log returns to Log. Fallback routes do not claim that native itself has browser deep-link parents.

## Shell and history behavior

`App.tsx` now renders the bottom navigation and resume pill only on the five tab routes. The pill floats above the bottom bar. Tab content with an active session reserves space for both controls. Root content reserves ordinary bottom spacing plus its safe-area inset, without a hidden-tab gap. Library's own header handles the top inset once.

The shell Back action, local plan Back action, and browser Back/Forward share the plan editor's discard prompt. Keeping edits retains the mounted draft. Explicit discard replays the requested destination. Successful save, confirmed deletion and duplication release the guard before their existing exit. Save failures retain the guard and draft; optimistic plan revision checking remains in `savePlanIfUnchanged`.

Browser entries carry an index, epoch and scroll position. Indexed cancellation returns to the original editor entry; confirmed navigation traverses the existing chain. Return navigation restores the source document scroll position. Tab-to-tab motion still follows tab ordering; root pushes and returns now report their own forward/back direction rather than inheriting the last tab swipe direction.

Unknown legacy entries cannot reveal traversal distance or direction. At that boundary the router retains the requested destination and pushes a replacement editor entry in a new epoch. This keeps the unsaved draft editable and makes explicit discard reach the destination. **The unknown forward tail is replaced at this exceptional boundary.** It is not possible to promise exact preservation of an arbitrary preexisting unindexed chain. Indexed navigation retains its chain. A restoration that cannot deliver its expected event falls back after 300 ms; it never silently discards the draft. Completion of restoration requires the matching index, epoch and editor route.

## Verification performed

- TDD route matrix: 15 dedicated detail routes hide tab controls and resume pill; five tab routes retain both; persisted active workout survives detail navigation.
- TDD regressions reproduced shell Back bypass, incorrect Calendar/Exercise Progress deep-link parents, missing source scroll recording, missing push/return direction, unindexed predecessor Back and a stalled restoration receiving an unrelated matching-index event.
- Focused Vitest run: **45 tests pass** across push-navigation, navigation-parity, existing plan-editor parity and existing motion parity suites.
- TypeScript check and production build pass. Build still reports existing font-resolution and large-chunk warnings.
- Real Chrome and WebKit CLI workflows on synthetic data at 390 × 844: browser Back cancellation/discard, Forward cancellation/discard, refreshed plan guard, saved-plan reload, successful save exit, active-workout resume, pill/bar geometry, detail footer and source-scroll restoration to 200 px pass.
- Chrome and WebKit legacy-boundary CLI workflows: pre-subscription unindexed Back cancellation/discard, forward reopen, and unindexed future Forward discard pass.
- Wider eight-suite run: 66 tests passed and the first journeys unit test timed out under parallel load; the journeys suite passed all nine tests when run alone. No source changes were made to mask that timeout.

Ignored reproducible browser helpers and screenshots are under `web/output/playwright/push-navigation-*`. Browser QA uses production assets and synthetic training records. WebKit reports its existing unsupported `interactive-widget` viewport-key warning; no app exception was observed in these checks.

## Limits

This slice did not run native builds, a native emulator, physical-device QA or installed iOS/PWA navigation. Native ownership above is source evidence; native runtime evidence is coordinated separately. Native HistoryCard expands inline while the web retains its preexisting dedicated history detail route. Settings destinations and modal editors that remain inside a tab are unchanged. Scroll position is restored; retained list search/filter state is outside this shell slice.
