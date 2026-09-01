# IronLog native app audit — 31 August 2026

## Verdict

IronLog has a stronger native foundation and a broader feature set than OpenGym. Its next improvement should be **agreement between features**, not another layer of features. A logged set, pain flag, workout date or imported session must mean the same thing to the workout editor, Home, recovery, statistics, Ledger and widgets.

I would not call the current working tree production-stable yet. The highest-priority findings concern destructive restore behavior, imported photo-file ownership and training prompts that can contradict pain restrictions. Passing the current test suite does not cover these paths.

The useful OpenGym references are explainable progression, detailed historical workout entry, visible evidence behind statistics and a compact next-action hierarchy. Its recovery formula and persistence architecture are not models to copy unquestioningly. See the [pinned OpenGym comparison](2026-08-31-opengym-reference.md) for feature-by-feature evidence and an independently reproduced warmup-counting defect in that project.

## Scope and evidence

- Inspected the native Kotlin application and its repositories, workout/draft flow, onboarding, plans, history, body tracking, recovery/intelligence, Ledger, settings, widgets, shared UI and relevant tests.
- Baseline: `feat/fatigue-suggestion`, HEAD `b851eb0575a07d6ca9487aa3a1ad710d189e6457`, **plus the existing dirty working tree**. Findings describe current files, not the July release or only committed HEAD. Line anchors may move as ongoing work is integrated.
- OpenGym: exact `DuarteSantos8/opengym` repository at `75fb168a03de09f995d05efd4fd2bfda2d595e0f`. Source and author-provided screenshots were reviewed; its APK was not installed. One isolated, synthetic pure-function recovery example was executed.
- Runtime work in this pass is confined to the API 36.1 QA emulator and synthetic instrumentation. No physical-phone update, data clear, Git push, release publication, website change or third-party media transplant was performed.
- **Source-confirmed** means the input-to-output path is evident in the current implementation; it does not claim a destructive test was run against personal data. **Runtime-confirmed** and **not yet measured** are called out separately. This is broad app coverage, not a claim that every line, device, theme combination or accessibility journey has been certified.

## Changes made during this pass

The implementation scope is the user's two current defects, not every finding below:

1. **Workout keyboard stability.** Reproduced first-set/header growth pushing the focused entry row down. Added consumption-aware IME layout and measured input-position preservation. A strengthened test also exposed the rest panel covering that focused row; the correction gives it a measured footer instead of relying on guessed bottom spacing. A delayed-save superset test then reproduced the old focused field pulling navigation backward: anchors now require pre-change visibility, while legitimate footer clamping and rapid pending changes remain supported. Set IDs, logging semantics, pending warmups and persistence are not changed by this layout work.
2. **Clearer, refractive navigation glass.** Reduced heavy blur, captured a bounded area outside the capsule, and bent actual surrounding backdrop pixels along its curved rim and moving selected lens. Added contrast protection and an explicit opaque compatibility path when refraction is unavailable or fails. The 64dp bar, five slots, 21dp icons, margins, off-state appearance and independent toggle are retained.

Android's custom AGSL material is an approximation, not Apple's proprietary renderer. The linked [Kotlin example](https://kotlinlang.org/docs/multiplatform/ios-liquid-glass.html) uses native iOS components; [Apple's design presentation](https://developer.apple.com/videos/play/wwdc2025/219/) explains the optical behavior that informed this treatment.

Final verification was completed after midnight on 1 September 2026 and is recorded below. The audit contains **27 prioritized findings: four P1 and 23 P2**, with product opportunities and unmeasured risks listed separately.

## Fix before a stable release

Priority labels: **P1** means resolve before calling the app stable; **P2** means important correctness/usability work. Product opportunities and unmeasured performance risks are labeled separately.

### P1 · D1 — Restore confirmation describes an update, but the operation replaces unrelated data

**Source-confirmed.** [DataPortabilityScreen](../../app/src/main/java/com/ironlog/app/ui/screens/settings/DataPortabilityScreen.kt#L243) tells the user matching stable-ID rows will be updated. It then chooses replacement for IronLog JSON. [ImportCenterScreen](../../app/src/main/java/com/ironlog/app/ui/screens/settings/ImportCenterScreen.kt#L177) has the same problem. [runConfirmedImport](../../app/src/main/java/com/ironlog/app/data/repository/ImportExportRepository.kt#L393) calls `clearUserDataTables` in replacement mode before importing.

Restoring an older backup therefore removes newer workouts, plans, measurements and other rows absent from that backup. Stable-ID upserts prevent duplicates *within the selected mode*; they do not make replacement a merge. The database transaction does not fix the misleading consent.

**Change:** explicit Merge / Replace choices, truthful domain counts, a preview of data that will disappear, and a recoverable pre-restore snapshot. Disable concurrent active-workout mutations during replacement.

**Acceptance:** restore an older synthetic backup into a database with newer unique workouts. Merge preserves them; Replace lists and removes them only after explicit confirmation. Cancellation and failure leave the database unchanged.

### P1 · D2 — A structurally unusable backup can pass preview and erase good data

**Source-confirmed; no destructive restore was run.** [previewImportPayload](../../app/src/main/java/com/ironlog/app/data/repository/ImportExportRepository.kt#L329) validates the envelope/version/data object but not the validity of every required row before setting `valid = true`. The replacement transaction then clears existing tables; [workout import](../../app/src/main/java/com/ironlog/app/data/repository/ImportExportRepository.kt#L930) silently skips blank IDs.

For example, this entirely synthetic payload passes the current envelope test but contains no importable workout:

```json
{"type":"ironlog_watermelon_export","version":1,"data":{"workouts":[{}]}}
```

Its array length can be counted as a workout even though its only row will be skipped. Committing an empty import after deletion is a successful transaction from the database's perspective.

**Change:** parse into a validated import model first; distinguish valid, invalid and unresolved rows; reject structurally invalid replacement payloads; never use raw array length as the promised imported count. Preserve intentional, explicitly confirmed empty backups as a separate case if supported.

**Acceptance:** invalid IDs/types/parents and unsupported versions fail before any deletion. Injected write failure rolls back. Preview counts equal actual imported rows.

### P1 · D3 — Imported photo URIs can become arbitrary app-writable deletion targets

**Source-confirmed.** [Photo import](../../app/src/main/java/com/ironlog/app/data/repository/ImportExportRepository.kt#L1043) stores `file_uri` from a backup verbatim. [Clear All Photos](../../app/src/main/java/com/ironlog/app/ui/screens/body/ProgressPhotosScreen.kt#L748) subsequently calls `File(uri.path).delete()` for any `file://` URI, without requiring ownership by the progress-photo directory.

A crafted backup can make a photo row refer to another file the app itself is allowed to delete, such as an app-owned backup. Android's sandbox limits the reach; this is **not** arbitrary deletion of every file on the phone. It is still an unsafe trust boundary.

**Change:** imported image references must be validated or marked unavailable. Resolve canonical paths and require containment in the app-owned photo directory before deleting. Keep owned copies distinct from external gallery originals and foreign-device URIs.

**Acceptance:** a disposable test file outside the owned photo directory survives every photo-delete path; owned image cleanup works; gallery originals are never removed.

### P1 · I1 — Daily Proof and widgets can recommend training despite a pain restriction

**Source-confirmed.** [Home recovery](../../app/src/main/java/com/ironlog/app/ui/screens/home/HomeScreen.kt#L274) includes pain flags. [GamificationViewModel.computeReadinessScore](../../app/src/main/java/com/ironlog/app/ui/viewmodel/GamificationViewModel.kt#L498) and [WidgetDataRepository](../../app/src/main/java/com/ironlog/app/widget/WidgetDataRepository.kt#L80) calculate readiness from history without the same current input. [Daily Proof](../../app/src/main/java/com/ironlog/app/domain/gamification/GamificationSummary.kt#L128) can consequently say “Fresh enough to press”; its inactivity branch can also say “Train today” regardless of a pain restriction.

**Change:** one typed readiness/restriction snapshot, consumed everywhere. A pain flag is not merely another score component: action eligibility and wording must honor it before motivation or streak logic. Missing evidence must not become affirmative reassurance.

**Acceptance:** identical history, clock and check-in produce identical restrictions in Home, recovery, Daily Proof and widgets. A flagged region never receives an unrestricted recommendation because a streak is at risk.

## Tracking and program intelligence

### P2 · T1 — Clearing RPE/RIR does not clear the database value

The [set editor](../../app/src/main/java/com/ironlog/app/ui/components/SetRow.kt#L324) offers blank-to-clear behavior, but [WorkoutRepository.updateSet](../../app/src/main/java/com/ironlog/app/data/repository/WorkoutRepository.kt#L269) applies RPE/RIR only when the input is non-null. The UI can show the field cleared while the old effort returns after rehydration and still affects downstream calculations.

Use an explicit update type: unchanged / clear / value. Test edit → clear → minimize → process restart → history and recovery, for both effort scales.

### P2 · T2 — Weighted bodyweight exercises bypass the progression engine

[Session normalization](../../app/src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt#L3360) emits `bodyweight_plus_weight_reps`, while [ProgressionRecommendationEngine](../../app/src/main/java/com/ironlog/app/domain/intelligence/ProgressionRecommendationEngine.kt#L19) accepts only `weight_reps`, `reps` and `bodyweight_reps`. A weighted pull-up/dip therefore falls out of the current recommendation path.

Use a shared tracking-type contract with explicit added-load semantics. Test the **real normalized exercise → engine** adapter, not only an isolated engine supplied an artificial `reps` type. Preserve the distinction between external load and total system mass.

### P2 · T3 — Timed weighted sets can be treated as rep-based 1RM/PR records

The [active workout PR path](../../app/src/main/java/com/ironlog/app/ui/screens/workout/ActiveWorkoutScreen.kt#L453), [statistics projection](../../app/src/main/java/com/ironlog/app/ui/viewmodel/StatsViewModel.kt#L176) and [intelligence projection](../../app/src/main/java/com/ironlog/app/domain/intelligence/TrainingIntelligenceEngine.kt#L245) can send the numeric `reps` field to a strength estimator without preserving timed-tracking meaning. For a weighted hold, that number represents seconds. Increasing hold duration must not invent a larger barbell-style 1RM.

Introduce typed performance records: load×reps, added-load bodyweight, duration, distance, etc. Gate each PR/volume/estimate by type; show the source set/date and estimate eligibility. Test warmups, timed holds, cardio, high reps, edits and deletions across all consumers.

### P2 · T4 — “Credited proof” still admits invalid sets and cardio warmups

[CreditedProof](../../app/src/main/java/com/ironlog/app/domain/gamification/CreditedProof.kt#L14) counts a working set when weight **or** reps is positive. A positive load with zero repetitions can count toward the eight-set threshold. The cardio-minutes path sums every set, including warmups, and identifies cardio with substrings: `run` also occurs in `crunch`. [RecoveryReadinessEngine](../../app/src/main/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngine.kt#L49) filters warmups but not all invalid/empty working rows, so its dose can disagree with other engines too.

Replace these independent heuristics with one typed, finite, completed-working-set predicate and explicit cardio duration conversion. Non-qualifying activity may remain visible in History, but should not masquerade as qualifying Ledger proof.

### P2 · I2 — A saved manual recovery check-in can disappear on reload

[StatsViewModel](../../app/src/main/java/com/ironlog/app/ui/viewmodel/StatsViewModel.kt#L69) writes a JSON setting, then reads it with `getSettingString`. [SettingsRepository](../../app/src/main/java/com/ironlog/app/data/repository/SettingsRepository.kt#L27) returns a `JsonElement` for that setting type, so the String cast fails. The observer can reload shortly after saving, not only after a full restart.

Choose one typed serialization path and migrate legacy quoted JSON intentionally. Test immediate observer refresh, process recreation and the 48-hour expiry boundary. A missing check-in and an explicitly neutral check-in must remain distinguishable.

### P2 · I3 — Widget history drops time-of-day and exercise metadata

[WidgetUpdateWorker](../../app/src/main/java/com/ironlog/app/widget/WidgetUpdateWorker.kt#L76) builds a separate history mapper that turns start timestamps into date-only strings and omits muscle/equipment/category plus some set-type flags. Recovery then interprets that date at a different time than the in-app full timestamp; an evening workout can appear many hours older in the widget.

Share a full-fidelity history/recovery projection, with the same clock, metadata, check-in, restrictions and goal settings. Test an evening workout around midnight and failure/AMRAP sets; do not merely compare total set counts.

### P2 · I4 — Unknown custom exercises manufacture Core fatigue

[resolveRegionContribution](../../app/src/main/java/com/ironlog/app/domain/intelligence/RecoveryReadinessEngine.kt#L93) defaults an unresolved muscle to `Core`. An unmapped custom movement is not evidence of Core training. Other intelligence paths omit unknown contribution, so the screens disagree.

Use explicit unknown/unmapped coverage, prompt for optional muscle metadata, and state what was excluded from the estimate. Do not invent anatomical attribution.

### P2 · I5 — Recovery can remain stale after time passes or the app resumes

[Home](../../app/src/main/java/com/ironlog/app/ui/screens/home/HomeScreen.kt#L274) and [RecoveryMapScreen](../../app/src/main/java/com/ironlog/app/ui/screens/recovery/RecoveryMapScreen.kt#L130) remember calculations by history/input, without a consistent clock/resume invalidation key. If those inputs do not change, elapsed recovery time alone need not trigger recomputation.

Use one injected clock and a lifecycle-aware snapshot refresh on resume, history/check-in changes and a sensible visible-screen time cadence. Do not recompute whole history every animation frame or add another polling loop per screen.

### Intelligence improvements after correctness

These are product/research opportunities, not claims of a clinically validated model:

- Each prescription should expose its source session, policy, missing inputs, confidence and loadable increment. The current conservative effort-aware progression rules are worth preserving.
- Show exercise/muscle coverage, rated/total working sets and freshness of check-in data. An attractive `78` is less useful than an explained estimate with its limitations.
- Unknown history should read as insufficient evidence, not default physiological certainty. The current empty-load map can look fully ready even without a meaningful baseline.
- Plan-day ranking currently emphasizes exercise names and broad region readiness. Improve it with planned dose, secondary-muscle exposure, available equipment, schedule constraints and explicit restrictions, after the shared data contract is fixed.
- Keep injury/pain messaging distinct from ordinary post-training fatigue. Do not present an exact readiness percentage as a medical clearance.
- A biometric snapshot is loaded in Home, but the inspected `blendWithBiometric` helper has no production caller. Do not advertise biometric-adjusted readiness until the integration is actually connected, tested and clearly disclosed.
- Papers can motivate principles—effort, dose, recovery variability and useful self-report—not validate IronLog's exact constants. Validate model calibration prospectively before making accuracy claims. The [existing intelligence research review](../program-intelligence-audit-2026-08-31.md) is context, not evidence that every UI consumer is wired correctly.

## Gamification, dates and progress

### P2 · G1 — Calendar/streak projections mix UTC dates with local boundaries

[StatsViewModel](../../app/src/main/java/com/ironlog/app/ui/viewmodel/StatsViewModel.kt#L159), [StatsScreen](../../app/src/main/java/com/ironlog/app/ui/screens/stats/StatsScreen.kt#L159) and [Home helpers](../../app/src/main/java/com/ironlog/app/ui/screens/home/HomeScreen.kt#L1346) strip the date from UTC timestamp strings while comparing with local dates. A workout at **00:15 on 31 August in Asia/Kolkata** is `2026-08-30T18:45:00Z`; substring parsing assigns it to 30 August. Another PB path correctly converts to the local zone, allowing different screens to disagree.

Use one deliberate local-date/date-only policy with an injected `ZoneId`. Test midnight, Monday, ISO week-year changes, DST and imported date-only values in both Asia/Kolkata and America/Los_Angeles.

### P2 · G2 — Recovery Circuit can consume the week's opportunity without awarding its promised XP

[StatusWindowScreen](../../app/src/main/java/com/ironlog/app/ui/screens/gamification/StatusWindowScreen.kt#L419) can offer the circuit before the exact qualification point. [completeRecoveryCircuit](../../app/src/main/java/com/ironlog/app/ui/viewmodel/GamificationViewModel.kt#L335) requires exactly `weeklyGoal - 1` credited sessions to award the event, but its else branch still records the week as completed.

Example: with a goal of three and only one credited session, completing it can consume the once-per-week state without the advertised bonus. Reaching two sessions later does not undo that consumption.

Use one transactional eligibility result shared by the button, explanation and write. If ineligible, neither award nor consume the reward opportunity; optionally allow an explicitly non-reward recovery activity separately. Test double taps, concurrent refresh and ISO-week transitions.

### P2 · G3 — Widgets mark “done” using different evidence from Ledger

[WidgetDataRepository](../../app/src/main/java/com/ironlog/app/widget/WidgetDataRepository.kt#L101) uses broad history counts for today/weekly completion while XP/streak calculations use credited proof. Warmup-only or otherwise non-qualifying history can therefore show completed workout feedback without corresponding Ledger progress.

Either share the qualifying predicate, or clearly label two different concepts: “activity logged” and “Ledger proof earned.” Do not silently conflate them.

### P2 · G4 — History-only imports do not reconstruct all historical streak badges

[evaluateAppBadges](../../app/src/main/java/com/ironlog/app/ui/viewmodel/GamificationViewModel.kt#L513) evaluates current daily streak. A native athlete who achieved a streak in the past keeps the additive unlock; importing that same past history without its profile can leave the badge locked because the streak is no longer current.

For historical achievements, evaluate the historical maximum/first threshold date as well as current state. Keep earned unlocks additive and deterministic; test native versus history-only import with the same workout facts and no existing badge cache.

### Gamification design decisions to settle

- Keep fresh onboarding at zero earned rewards; calibration is a training profile, not proof. Include import, restore and profile migration in that invariant, not only the onboarding preview test.
- Clarify “qualifying week” versus “weekly goal completed.” Some Ledger and streak surfaces use those terms for different thresholds.
- Global integrity penalties can reduce historical XP when later low-quality sessions are added. Decide whether that retroactive behavior is intentional; explain it if retained. This is a product-policy question, not an automatically classified implementation defect.
- Preserve IronLog's distinctive Ledger, durable badges and Forge Fox. OpenGym has useful restrained presentation, but not an equivalent deeper XP/rank system to import.

## UI and accessibility

### P2 · U1 — Plan Editor can reorder the wrong exercises

[PlanEditorScreen](../../app/src/main/java/com/ironlog/app/ui/screens/plans/PlanEditorScreen.kt#L156) subtracts a hardcoded three header items, but four LazyColumn items precede the exercises. With A/B/C at indices 4/5/6, dragging A over B can reorder B/C instead; moves involving the last item can fail the bounds guard. This is deterministic indexing evidence, not just a visual complaint.

Resolve drag keys against stable exercise IDs. Test first/last moves and extra header insertion; provide accessible Move up/Move down alternatives.

### P2 · U2 — Individual photo deletion hides the row but leaves its private copy

[Individual delete](../../app/src/main/java/com/ironlog/app/ui/screens/body/ProgressPhotosScreen.kt#L540) removes the database row without removing the owned image file. Clear All later enumerates surviving rows and misses that orphan.

Centralize owned-file cleanup and row deletion, with error reporting and a narrowly scoped orphan reconciler. This is a privacy/storage defect, not evidence that the photo has been exposed outside the app. Apply the ownership fix from D3 first.

### P2 · U3 — “View” can open a previous comparison instead of the tapped photo

[ProgressPhotosScreen](../../app/src/main/java/com/ironlog/app/ui/screens/body/ProgressPhotosScreen.kt#L527) only sets `selectedA` if it is null and does not clear `selectedB`. After choosing A/B for comparison, tapping View on C can reopen the old A/B comparison.

Use mutually exclusive viewer states: `Single(photoId)` or `Compare(aId, bId)`. Keep list selection separate from viewer state. Test changing dates, deleting a selected photo and viewing an unrelated row.

### P2 · U4 — The photo viewer is not a proper modal navigation/focus boundary

The [full-size viewer Box](../../app/src/main/java/com/ironlog/app/ui/screens/body/ProgressPhotosScreen.kt#L565) has no viewer-specific Back handler; normal Back can leave the Photos route instead of just closing the viewer. Its keyboard, safe-area and underlying-focus behavior also need runtime checks.

Use a dialog/fullscreen route with deliberate Back dismissal, dirty-note policy and focus restoration. Do not assume visual full-screen coverage makes an overlay modal to accessibility services.

### P2 · U5 — Recovery details lack a non-pointer alternative; pain hatching uses mismatched keys

[RecoveryMapScreen](../../app/src/main/java/com/ironlog/app/ui/screens/recovery/RecoveryMapScreen.kt#L470) exposes muscle selection through raw geometric tap handling without equivalent named semantic actions or a textual chooser. Separately, its grouped pain flags (`Push`, `Legs`, etc.) do not match the lowercase anatomical piece keys tested by [BodyMapCanvas](../../app/src/main/java/com/ironlog/app/ui/screens/body/BodyMapCanvas.kt#L245), so the dedicated pain-hatch branch cannot match the current flags. Normal readiness coloring can still appear red; the missing element is the specific pain marking.

Use a typed group-to-region map shared by color, hatching, text and hit testing. Add a readable muscle list with named actions, readiness and pain state; keep the visual Canvas as an additional representation.

### P2 · U6 — Onboarding day controls omit meaningful names and selected semantics

[Step4Quota](../../app/src/main/java/com/ironlog/app/ui/screens/onboarding/steps/Step4Quota.kt#L34) uses M/T/W/T/F/S/S with visual-only selection. Screen readers cannot distinguish Tuesday/Thursday or Saturday/Sunday, or reliably announce selection from those labels alone.

Use full accessible day names, toggleable/selected semantics and an explicit group. Similar chip/card selection controls should reuse the accessible patterns already present in TypographySettingsCard. The 40dp visual box alone does not prove its expanded Compose touch target is only 40dp; measure targets rather than assuming.

### UI refinements worth doing next

- Keep Today / Resume workout as the primary action. Show optional coaching, recovery detail and Ledger depth progressively instead of making every card equally loud.
- The current active-workout Home screenshot repeats Resume in Daily Proof, the large workout card and the floating resume control. Give the main workout card one primary action; make Daily Proof a compact status and show the floating control where it adds navigation value rather than duplicating the same action three times.
- Keep logged set summaries compact; edit effort in a small focused control. Preserve >=48dp interaction targets even when the visible decoration is smaller.
- Use the existing separate card-gap, content-gap and inner-padding roles consistently. Do not scale fonts, drawing geometry or system insets with spacing sliders.
- Audit long plan names, custom exercises and high text scales in onboarding registration, the seven-day quota row and ProgramPicker's currently non-scrollable detail content. These are **layout risks requiring screenshots**, not all verified clipping bugs.
- Keep menus/dialogs opaque. Navigation glass is a scoped visual effect, not a reason to reintroduce transparent menus over workout controls.
- Preserve the corrected measured body-map viewports. Historical screenshots of earlier alignment failures are not proof the current alignment remains broken.

## Performance and lifecycle

No comparative FPS, battery, startup or large-database timings were measured. These findings identify code paths to benchmark and improve, not invented performance scores.

### P2 · PERF1 — Nested ViewModels have no owner-driven cleanup

[AppDataViewModel](../../app/src/main/java/com/ironlog/app/ui/viewmodel/AppDataViewModel.kt#L28) constructs PlansViewModel and StatsViewModel directly. Their independently launched scopes/observers are not cleared with the parent. Re-entering routes that create these containers can retain redundant subscriptions.

Inject repositories/use cases into a lifecycle-owned ViewModel, or obtain properly graph-scoped shared ViewModels. Verify observer counts and allocations after repeated route entry/exit.

### P2 · PERF2 — Unrelated writes invalidate all completed history

[StatsRepository.observeStatsChanges](../../app/src/main/java/com/ironlog/app/data/repository/StatsRepository.kt#L35) subscribes to whole workout/exercise/set/settings queries and discards their lists into an invalidation signal. [StatsViewModel](../../app/src/main/java/com/ironlog/app/ui/viewmodel/StatsViewModel.kt#L51) then rebuilds completed history and derived records. Active set edits and unrelated settings can trigger the full projection; the nested-owner issue amplifies it.

Narrow invalidation, share projections and avoid materializing whole query results just to emit Unit. This work already uses IO dispatching, so do not mislabel it as a proven main-thread scan.

### P2 · PERF3 — Home polls settings while long-lived composition remains active

[HomeScreen](../../app/src/main/java/com/ironlog/app/ui/screens/home/HomeScreen.kt#L175) repeatedly queries active-workout and recovery/pain settings at 1.5s/1.2s intervals. Those loops are composition-scoped, not explicitly lifecycle-STARTED scoped. Similar repeated state gathering exists in navigation.

Prefer observed repository flows and lifecycle-aware collection. Measure background wakeups/query counts; stop repeated work when the screen is not active. Do not replace these loops with another independent polling-based snapshot.

### Geometry and query ownership

- [Body-map loading](../../app/src/main/java/com/ironlog/app/ui/screens/body/BodyMapCanvas.kt#L153) parses assets and computes sampled bounds synchronously from per-screen `remember` calls. Precompute/cache one immutable dataset off the main thread; share it between Home and recovery.
- [ObjectBoxFlow](../../app/src/main/java/com/ironlog/app/data/repository/ObjectBoxFlow.kt#L8) cancels the subscription but does not explicitly close the owned Query. Define ownership and close both resources; test repeated short-lived collectors.
- During a cold emulator resume, the workout briefly displayed “Workout / 0 exercises” before the planned six exercises appeared. Add an explicit loading state and gate premature actions; this observation is not a measured startup benchmark or evidence of lost exercise data.
- JVM runs emit ObjectBox cross-thread read-transaction cleanup warnings in test teardown. The inspected issue involves pooled thread-local read resources and the thread used to close the test store. It is **not evidence of production database corruption**. Improve fixture ownership/cleanup instead of ignoring the warnings or conflating them with the Query lifecycle finding.

Benchmark a minified release with 0 / 1,000 / 10,000 synthetic workouts: cold start, first Home/recovery render, set commit latency, scrolling frame time, navigation churn, background query count and memory after repeated entry/exit. Keep stable IDs, ObjectBox transactions and targeted queries; OpenGym's whole-state JSON cloning is not a better scaling foundation.

## Additional data-safety hardening

### P2 · D4 — “Validate Latest Backup” does not validate a backup

[BackupCenterScreen](../../app/src/main/java/com/ironlog/app/ui/screens/settings/BackupCenterScreen.kt#L202) sets `last_backup_health = ok` and refreshes a file list. It does not parse the selected backup, verify relationships, decrypt/check integrity or even require a usable latest file before marking health OK.

Show the inspected filename/date, actual validation result, valid/importable counts and limitations. Never claim a backup is healthy solely because the button was tapped.

### P2 · D5 — Legacy API-key fallback can cross provider boundaries

[CloudAiKeyStore.load](../../app/src/main/java/com/ironlog/app/domain/intelligence/CloudAiKeyStore.kt#L52) returns the legacy shared key when the chosen provider slot is empty. On an upgraded installation that still has that legacy key, switching to another/custom endpoint can therefore reuse a key from the former provider. Clearing a provider slot does not clear the legacy fallback.

Migrate once to the identified provider, then remove the shared fallback. An unconfigured provider must be genuinely unconfigured. Test provider switching and clear/reload with synthetic strings; do not log or expose real keys.

## Recommended order of work

| Order | Deliverable | Exit criterion |
|---|---|---|
| 1 | Restore validation/consent, file ownership, pain-action gating | Malformed data cannot delete valid records; external photo references cannot delete unrelated files; all training CTAs honor restrictions. |
| 2 | One typed training/history snapshot | UI, recovery, intelligence, Ledger and widgets agree for the same facts, clock and zone. Invalid/timed/warmup inputs are handled consistently. |
| 3 | Tracking and earned-progress integrity | Effort clears persist; weighted bodyweight progression works; temporal/import/recovery-circuit regressions pass end to end. |
| 4 | Workout/plan/photo interaction cleanup | Stable-ID reorder, proper photo viewer/delete behavior, keyboard-safe measured layout and accessible equivalents. |
| 5 | Lifecycle and performance work | Measured query/frame/allocation reductions on large synthetic histories, with no background polling regression. |
| 6 | Selected OpenGym-inspired capabilities | Detailed backfill, explainable progression policies, evidence/coverage labels and optional next-session notes, implemented originally in Kotlin. |

Do not combine all six into another unbounded redesign. Land behaviorally tested slices so regressions can be attributed and user data stays recoverable.

## Verification in this pass

### Final automated results

- **289 JVM tests passed**, zero failures/errors/skips.
- Debug app and instrumentation APKs built successfully and were installed with `-r` into the separate `com.ironlogpro.app.debug` QA package on the API 36.1 emulator.
- **38 normal-motion UI tests passed.** The package lists 39 cases; the system-animation-off case is intentionally assumption-skipped in that run. It was then run separately with animator duration scale zero and **passed**. The emulator setting was restored to one. There were no failures in the final package run.
- Debug lint completed with **0 errors, 161 warnings and 22 hints**. This is not a warning-free build.
- No AndroidRuntime fatal entries were observed in the final emulator log check.

| UI coverage | Distinct passing cases | What was verified |
|---|---:|---|
| Workout keyboard | 7 | Consumed ancestor insets, first-set/header growth, scrolled input anchor, taller measured footer, separate set insertions, last-set/footer removal, keyboard reopen, delayed superset write with retained source focus and with actual focus transfer. |
| Navigation glass | 8 | Preserved geometry across 12 themes, 320/411dp widths and 1.0/2.0 font scales; 21dp icons; blocked underlying taps; off-state pixels; shader compilation; live backdrop updates; fallback; idle settling and disabled system animation. |
| Glass optics/contrast | 5 | Fine-detail center, sampling beyond the curved rim, unavailable/failed-effect opaque fallback, and paired rendered-background/quantized-foreground contrast for all themes and the pressed selected lens. |
| Glass preferences | 2 | Accessible switch, visible retryable save failure, persistence, and independence from card shine. |
| Other regressions | 17 | Bundled fonts, card shine, cloud-mode gating in Stats and workout debrief, compact effort/set controls, equal carousel pills, and opaque dropdown interaction/dismissal. |

The shine test initially failed intermittently while waiting only for an IO-backed setting rather than its rendered application. A test-only committed-provider/save-completion barrier replaced that timing assumption. Production shine code was not changed; the final full run passed its original moving/static pixel and unchanged-size assertions.

### Failing regressions established before fixes

- Original first-set/header movement was reproduced with the actual numeric keyboard. Tests first failed for missing IME consumption, then approximately 104dp of anchor movement, then rest-panel occlusion. The input bottom reached 320.38dp while the panel started at 272dp; the taller-panel case started at 216dp.
- The delayed superset fixture navigated successfully, retained source-field focus, and published the saved source set afterward. Its destination disappeared before the pre-change visibility guard; the same unchanged assertion now passes.
- The optical tests use actual rendered fine stripes and a colored source band wholly outside the capsule. The old renderer failed. Null/failed refraction and insufficient rendered contrast also produced failing regressions before their fixes. The final ink target includes headroom for display quantization rather than relying on a rounding tolerance.

### Actual app replay and limits

On the QA emulator, real keyboard replay kept the entry row at exactly the same pixel position for first-set logging, second-set logging and deletion from two sets to one. Last-set deletion, keyboard close/reopen, dragging immediately after logging, force-stop/resume with a retained working set, imported exercise notes and ordinary superset auto-advance were also exercised. A final-APK replay with the input initially at the keyboard boundary correctly moved it upward by the measured footer space when rest appeared, retaining focus rather than covering the input or dismissing the keyboard. The rest controls remained in measured space above the keyboard. Screenshots and UI dumps are under ignored `artifacts/ime-*`, including `ime-handoff-logged.png`; glass captures include `glass-home-final.png`, `glass-optics-clear-center.png` and `glass-optics-curved-rim.png`. These local QA artifacts are not public assets or release fixtures.

The emulator's SwiftShader/headless process earlier exited with a Windows access violation during app launches. Testing continued with the host GPU and snapshots disabled. That host failure is not an observed AndroidRuntime app crash, and the physical phone was not used as a workaround.

**Not certified by this pass:** physical-device behavior, the full active-workout landscape/compact-height/large-font keyboard matrix, end-to-end TalkBack traversal, old-Android GPU behavior, comparative FPS/battery/large-history performance, a signed release, or every overlay on every route. The bounded viewport and tall-footer cases do not replace that full matrix. OpenGym's APK was not run. The wider audit findings above are **reported, not silently fixed in this visual/keyboard pass**; no push or phone update was performed.
