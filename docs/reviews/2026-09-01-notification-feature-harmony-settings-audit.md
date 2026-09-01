# IronLog notification, feature-harmony, and Settings audit — 1 September 2026

## Status

This document describes the current dirty working tree. “Implemented” means the code is present in the workspace; it is not a release claim until the verification log at the end is complete.

The audit follows one rule: a setting or action is only considered functional when its saved state reaches every consumer that promises to use it, survives process recreation, and has a visible failure path.

## Notification lifecycle

### Implemented in this pass

- Workout notifications now have one explicit session owner and one serialized action queue. Start, pause, resume, rest-timer, discard, finish, and service teardown no longer issue independent racing notification mutations.
- Discard and finish stop the foreground service and remove the foreground notification. A stopped or missing service has a direct cancellation fallback, covering the stale notification shown after discard.
- Notification action PendingIntents use immutable, occurrence-specific identities. Old reminder actions cannot mutate a newer occurrence.
- Workout action entry points are private to the app. Notification deep links pass through a private routing activity instead of exporting workout mutation receivers.
- Rest-timer state is persisted as a deadline, not a decrementing in-memory counter. Locking, process suspension, and reopening recalculate remaining time without fabricating elapsed ticks.
- Reminder scheduling has one next-occurrence calculation, quiet-hour policy, lateness limit, foreground suppression, and bounded retry path.
- Notification permission, channel availability, global-notification setting, per-feature setting, and platform denial are reported as separate delivery outcomes.
- Restore/import paths reconcile scheduler and active-workout notification state after the data transaction finishes.
- Automatic backup scheduling uses a latest-wins mutation coordinator. The setting is not confirmed until WorkManager accepts the corresponding schedule/cancel operation.
- Every foreground resume now reconciles the active-workout service against persisted workout state. A discarded or completed workout cannot keep a stale ongoing notification alive merely because an earlier service stop was missed.

### Remaining runtime checks

- OEM-specific lock-screen delivery, exact-alarm policy, Doze delay, and notification-channel user overrides need a physical-device matrix. Code cannot override a channel the user disabled in Android Settings.
- A Samsung update-in-place replay must verify discard, force-stop/resume, rest completion, and reminder actions without clearing app data.

## Feature-harmony findings

### Correctness changes implemented

- Warmup/rest/gamification evidence is awarded only after the corresponding persisted action succeeds. Merely opening a timer or changing a preference is not training proof.
- Goal-mode exploration is credited only when a new qualifying workout is completed in that mode. Repeated refreshes and settings changes cannot mint the badge.
- Creating a starter plan during onboarding does not unlock the user-created-plan achievement. A deliberate plan creation does.
- The obsolete duplicate `s_rank` badge is removed from definitions and filtered during compatibility reconciliation.
- Body-weight entry converts display pounds to canonical kilograms at the repository boundary.
- History editing converts stored instants to the device zone, and the editor can explicitly clear notes and rating rather than treating null as “unchanged.”
- Custom-exercise deletion failures remain visible and retryable instead of crashing the coroutine or pretending the row was removed.
- Onboarding and Settings share canonical intelligence values. Legacy `LOCAL`/`AUTO` values migrate deterministically instead of selecting contradictory engines on different screens.
- The exact weekdays chosen in onboarding are persisted and consumed by scheduling context; a weekly count no longer silently replaces the chosen days.
- AI plan gym-profile input uses the same canonical equipment schema and excludes explicitly unavailable equipment.
- Completed-history deletion, PR reset cutoffs, projections, active-workout baselines, and intelligence velocity use one reset boundary.
- Plan import now uses one awaited, transactional codec. Unknown named movements are recreated as custom exercises, and descriptions, exercise notes, warmup flags, supersets, malformed/skipped counts, and canonical export metadata stay consistent across entry routes.
- Imported plan-exercise order accepts the canonical and supported legacy aliases, normalizes duplicate or sparse positions deterministically, and persists the compacted order before reporting success.
- Reordering plan cards no longer silently changes the active program; activation remains an explicit menu action.
- Health Connect is now an explicitly read-only context surface. The app requests only the recent sleep, resting-heart-rate, and HRV records it actually displays in Settings; it no longer requests unused write scopes, silently prompts from Home, or claims that those values alter readiness.
- Partial Health Connect grants remain usable and are described per metric; denying one optional record type no longer hides every granted signal or implies full access.
- Completed-history clearing and PR-baseline reset are awaited destructive transactions. Their dialogs stay open on failure, show a retryable error, and completed-history clearing preserves any active workout.
- Settings and Stats now route history import, completed-history clearing, and PR reset through one application-owned mutation coordinator. Accepted work survives route recreation, overlapping mutations are blocked, CSV payloads are snapshotted before dispatch, and Home/History/Stats/Gamification/reminders/widgets reconcile independently after the terminal database commit.

### Connections still requiring a product decision or a larger slice

1. **Biometric readiness.** Health Connect values deliberately remain context-only. A future readiness contribution needs personal baselines, measurement-quality rules, one repository-backed model for Home/Recovery/widgets, and an explanation of confidence; the current UI must not claim biometric-adjusted readiness.
2. **Performance mode.** `Auto / Battery / Balanced / Max` is saved but has no runtime consumer. It needs a precise contract—animation/refraction quality, refresh cadence, or removal. It must not imply faster training calculations without evidence.
3. **Progression policy.** Global style, plan rules, and exercise overrides exist, while parts of active logging still construct a conservative default. A single resolved policy object should be shown with its source and used by every suggestion.
4. **Appearance backup.** Typography, spacing, shine, and glass currently use platform preferences separate from the canonical IronLog backup. Either include a versioned appearance payload or label them device-local.
5. **Historical exercise snapshots.** Library edits can change names or metadata presented for old workouts. Completed history needs immutable exercise display/tracking snapshots before library editing can be described as non-destructive.
6. **Body-weight intelligence input.** A newly saved weight and the onboarding baseline must converge on one current-athlete value; otherwise load and recovery explanations can use stale profile context.

## Settings UI audit

The current screen contains eight top-level sections and more than a dozen embedded controls in one long route. It mixes frequent workout preferences, one-time setup, appearance experimentation, external integrations, destructive data actions, and app information. That makes scanning difficult and raises the chance that a user changes the wrong similarly weighted row.

### Safe improvements already implemented

- Rows now meet a minimum 48 dp interaction height.
- Persisted values hydrate authoritatively after repository initialization without overwriting a control the user has already changed during startup.
- Cloud-provider keys load off the main thread, and Cloud AI, rest-timer, and bar-weight saves are awaited. Validation or storage failures remain visible and retryable instead of dismissing the editor as though the write succeeded.
- Cloud-provider key and settings commits run inside a cancellation-safe boundary. A failed settings write rolls the key back where possible and leaves an actionable error instead of a half-configured provider.
- Keep-awake changes are awaited and roll the visual switch back when persistence fails.
- Radio and switch rows expose selected/checked semantics, API-key visibility has an accessible label, headings are announced as headings, and live errors use live-region semantics.
- Theme copy labels browser-style dynamic color honestly as `Monet (Fallback)` on devices where wallpaper-derived colors are unavailable.
- Test-notification, Android channel settings, and tutorial restart use the full row as the target instead of a small text fragment.
- The list respects navigation-bar and keyboard insets.
- CSV export runs off the UI thread, exposes an error, and opens an explicit share chooser only after the file is written.
- Async settings mutations are serialized and optimistic state is not allowed to reorder writes.
- Process-owned destructive mutations expose one in-flight state to every route, so navigating away and back cannot create a second import, history clear, or PR reset while the first is still accepted.
- Backup restore options expose one selectable target per row, and the shared metadata typography tokens now enforce a 12 sp minimum instead of allowing essential 9–11 sp text.

### Visual and accessibility findings

- Static inventory in the current UI tree finds 43 raw `AlertDialog` calls, 20 raw modal sheets, 7 dropdown-menu call sites, and only 16 explicit semantics blocks across 118 direct clickable modifiers. Counts are a triage signal rather than proof that every call site is defective, but they identify where shared overlay and interaction contracts are not yet systematic.
- The initial scan found 51 plain `collectAsState` call sites. Route-visible flows in Active Workout, Stats, Plans, onboarding, intelligence, history, analytics, and appearance settings have been moved to lifecycle-aware collection. The ten remaining calls belong to the application-lifetime root host and deliberately follow the activity composition lifetime.
- The scan found twelve explicit 9–11 sp labels, concentrated in onboarding plus isolated Plan Editor and Gym Profile metadata. They have been raised to a 12 sp minimum; the cramped Plan Editor progression selector is now horizontally scrollable with 48 dp targets instead of shrinking its labels.
- The single-page hierarchy is too flat: profile, AI keys, themes, workout behavior, reminders, backup, and legal rows have nearly equal visual weight.
- Destructive actions appear in the same scrolling rhythm as ordinary navigation. They need a clearly separated danger area and an explicit consequence summary.
- Nine raw dialogs and one bottom sheet live in this screen. They should migrate to the shared opaque, inset-aware wrappers so focus restoration, IME behavior, blocked background taps, and error placement are consistent.
- Several binary or mutually exclusive controls communicate selection primarily through color. Selected/state descriptions and group semantics should be reused from the strongest existing controls.
- Cloud-provider configuration exposes advanced fields too early. Presets, connection state, and test result should be primary; URL/format/model details belong in a disclosed advanced area.
- The app-wide spacing slider should continue to affect only semantic spacing tokens. It must never scale text, icon geometry, touch targets, safe areas, or body-map transforms.

## Settings redesign directions requiring approval

### A. Training Console — recommended

A compact status header, followed by searchable grouped destinations: Training, Intelligence, Appearance, Notifications, Data & Privacy, and About. Keep only high-frequency toggles inline; each complex area gets a focused detail route with its own save/error state.

Why: shortest scan time, safest destructive actions, and room for honest integration status without making Settings feel like a form dump.

### B. Refined single scroll

Retain one route, add sticky group navigation, stronger section rhythm, progressive disclosure, and a separated danger footer.

Why: smallest navigation change, but the screen remains long and harder to test at 2× text.

### C. Visual control room

Use a compact two-column summary grid for Appearance, Training, Intelligence, and Notifications, with detailed routes underneath.

Why: more expressive and glanceable, but it risks dashboard-like visual noise and requires the most responsive-layout work.

No full visual redesign should be merged until one direction is selected. Functional and accessibility corrections above are independent of that choice.

## Verification log

Completed against the final source state in this pass:

- `:app:testDebugUnitTest --rerun-tasks`: **649 tests across 149 suites, 0 failures, 0 errors, 1 ignored benchmark**. Coverage includes notification ownership/actions, reminders, backup scheduling, onboarding normalization, history/PR reset, process-owned mutation cancellation, awaited refresh completion, plan import/order, gamification evidence, unit conversion, and timezone editing.
- `:app:lintDebug`: **0 errors, 163 warnings, 19 hints**. Remaining warnings are non-blocking cleanup/dependency items, led by `UseKtx`, unused resources, Compose autoboxing, newer dependency notices, and obsolete SDK guards.
- `:app:assembleDebug` and `:app:assembleDebugAndroidTest`: successful; the instrumentation suite compiles into an APK.
- `:app:assembleRelease` and `:app:bundleRelease`: successful with minification and release vital lint.
- Release APK: `versionCode=9`, `versionName=0.1.0-pre-alpha.8`, `minSdk=26`, `targetSdk=36`; 51,502,731 bytes; SHA-256 `18FCA0D6E3535170415DDC6E9B00A93915B852D8A729283C4FD76F048BEDB9B5`; one signer; APK Signature Scheme v2 verified. The monotonic code is above the previously distributed code 8, so Android can accept an update-in-place install without downgrade flags or data clearing.
- Release AAB: 61,254,967 bytes; SHA-256 `297D023B43283BC0FCC7B0E8C5162E064E9BC3B4505C42571CE59EA58BED2146`; JAR signature verified. The certificate is self-signed and not publicly trusted, which is normal for Android upload artifacts but must be protected as release material.
- Full decompressed-content and filename scans covered every APK entry (80,668,572 bytes) and every AAB entry (322,498,900 bytes). They found no private identity/path, QA fixture, private backup, `local.properties`, exact local signing password, high-signal API token, or embedded current keystore. The only backup-named entry is the legitimate AAB `backup_rules.xml`, whose source excludes root backup; the release manifest also has `allowBackup=false` and no `debuggable` or `testOnly` attribute.
- `git diff --check` passes. Static source scans find no Health Connect write-scope/helper remnants, no legacy destructive history/PR calls, and no explicit 9–11 sp UI labels. The ten remaining plain `collectAsState` calls are confined to the application-lifetime root host.

Not executed in this pass:

- emulator replay of notification lifecycle, lock/resume, discard, rest completion, import, history clear, and Settings at compact/large-text sizes;
- physical-device delivery and OEM notification behavior. A future phone install must use update-in-place installation and preserve existing data.

Release-key hygiene remains a publication blocker: the current local feature branch contains historical commits that once tracked the release keystore and matching signing passwords, even though those files are now ignored and the commits are not reachable from current remote branches. Do not push or merge those commits; rotate the key/passwords and clean the branch history before public distribution.
