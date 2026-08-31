# Web acceptance record

Status: implementation and hardening in progress; **not fully accepted**. The owner requested direct repository/preview publication on 31 August 2026. Deployment remains gated by CI; publication must not be described as native parity or physical-iPhone acceptance. All test data is synthetic. No APK, phone data or private backup was modified.

## Implemented journeys

- Native identity, current monochrome logo, all twelve themes, shared landing/app selection, native art and 24-role palette extraction.
- Lazy-launched working app inside a phone frame, with a separate full-screen mobile entry sharing saved data. Onboarding step changes reset scroll/focus; the landing is not a desktop dashboard.
- Saved onboarding steps, basic calibration/profile, goals and coaching preferences, starter selection, zero initial rewards.
- Templates, custom plans/exercises, editing, duplication, ordering, active selection, canonical and legacy JSON plans, notes and supersets.
- Persisted start/resume/finish/discard, stable set IDs, edit/delete/order, effort and notes, explicit warmup queues, rest deadlines, plate diagram and custom substitutions with session/plan scope.
- Editable History, date filtering, PRs, exercise/volume analytics, body measurements, photo storage/comparison, recovery check-ins/map, Ledger, durable badges and weekly Recovery Circuit.
- Built-in progression/next-day recommendations, basic program insights, manual external-AI prompt/paste-import workflow.
- Gym settings, units, theme, backup/restore/reset, privacy/research links, PWA manifest/icons/cache and safe-area layouts.

## Automated evidence

### Appearance update — 31 August 2026

- 94 JVM-independent web unit tests pass; production build and scoped scan of 95 output files pass. Original font bytes total 5.84 MiB; revisioned offline precache is approximately 7.75 MiB.
- All 47 browser tests pass on Windows with two workers. CI remains single-worker. New coverage loads every actual font family, all explicit weight presets, semantic heading weights, malformed preferences, storage denial, font-load failure, landing/iframe/full-screen synchronization, a complete workout after changing fonts, and offline selection of a previously unused font with the HTTP origin stopped.
- Global spacing is tested at 85% and 125%, with reset/reload, 320px and 200% text, preserved text/icon sizes and representative 48px-or-larger controls. WebKit testing exposed and fixed long header/tab/link overflow and native-select overflow; navigation labels now wrap and bottom clearance accommodates enlarged text.
- Both supplemental origin-stopped workout tests pass (2/2, 21 seconds). Typography and spacing received independent spec/code reviews; heading-role findings were fixed. Native code is developed in a separate dirty workspace and is not part of this website deployment.
- These are automated/emulated checks, not physical Safari, Home Screen or VoiceOver acceptance. Existing broader parity gaps below remain open.

The final run results should accompany this document. Current covered cases include:

- Units, dates, native calculation fixtures, baseline-zero onboarding, warmup exclusion, PRs, XP/streaks, durable badges and recovery eligibility.
- Rapid mutations, revisions, deletion/resume, finish ordering/idempotency, failed-write acknowledgment, restore rollback and orphan/malformed imports.
- Catalog v1→v2 migration, custom metadata precedence and repeated-template identity/backup integrity.
- Pending warmups across reload; real two-tab writes/deletion/completion in Chromium and desktop WebKit.
- Cached new-page startup, logging and completion with the actual HTTP origin stopped in Chromium and desktop WebKit; Chromium additionally checks offline-emulation banner behavior before reload.
- All twelve landing themes with automated contrast/control checks; 320/360/411 dp, 200% text and landscape landing layouts.
- Current-logo extraction, transparent ARGB conversion, opaque plate sheet, keyboard-editable number controls, and update deferral while saving.
- Chromium and desktop WebKit complete ZIP download/restore including synthetic photo bytes and malformed restore rejection.
- Latest phone-preview pass (Windows, 31 August): 85/85 unit tests, production build and scoped scan of 55 output files passed. Main browser suite: 29/29 passed in 2.6 minutes after correcting the offline test transport. Lighter landing weights are asserted separately from unchanged app heading weight 800.
- Phone-frame tests cover shared themes, saved onboarding/full-screen transfer, heading scroll reset, and a complete embedded workout with plate sheet and completed History. They pass in desktop Chromium/WebKit and Pixel 7 Chrome emulation. Outer-page and iframe overflow checks cover 320, 360, 700 and 768px; 700px was a failing regression before stacking the hero at intermediate widths.
- Representative sheets across all twelve themes passed opaque-container, forward/reverse keyboard focus, Escape/focus restoration and 320px/200%-text checks. A separate set-editor/options flow verifies cancellation and a backdrop tap over the underlying Minimize control does not navigate. Both Chromium and WebKit pass these cases (4/4).
- Supplemental origin-stopped offline startup/resume/log/finish tests passed in both engines (2/2, 22.0 seconds). See [the app-independent reproduction and limits](../diagnostics/OFFLINE-WEBKIT.md). Transparent marks, lighter landing typography and phone-contained onboarding/plate sheets were visually inspected; the font contains a real 100–900 variable weight axis.
- Build-output path/private-pattern checks and runtime dependency audit.

Screenshots and traces are local, ignored artifacts under `web/output/playwright/` and `web/test-results/`. Passing a route smoke test is not exhaustive overlay or accessibility coverage.

## Publication gates still open

1. Desktop WebKit's `context.setOffline(true)` reload returns an internal engine error. A minimal vanilla service worker reproduces the failure without IronLog/Workbox. The application acceptance transport now stops its own actual HTTP origin, keeping the reload/fresh-page/log/finish assertions and requiring an uncached request to fail. The app-independent emulation diagnostic remains unchanged and nonzero on failure. This isolates an emulation-path limitation, not a precise upstream defect, and does not prove shipping Safari or device-level airplane-mode behavior.
2. Real iPhone Safari and installed Home Screen testing: launch/offline/lock/unlock/termination, storage separation, backup/import, safe areas, keyboard, VoiceOver, photo pickers and timer resumption. Desktop WebKit does not satisfy this gate.
3. Full route × overlay × theme × text-size coverage, focus restoration/blocked background taps, storage denial/quota fault injection, and interrupted-update end-to-end testing. Existing screenshots are representative, not every permutation.
4. Reconcile remaining native feature differences before claiming 1:1 parity: full training calibration/schedule, native fuzzy/multi-primary exercise resolution, complete program-intelligence rules, finite plate inventory, advanced settings, individual non-grade badge artwork, and true visual wheel interaction. See `domain-parity.md` for data/calculation limitations. Current numeric controls intentionally use bounded accessible entry.
5. Website-only diff/CI review and Pages deployment verification. The owner requested a preview publication, not a claim that the other acceptance gates are closed. Failed CI must block deployment.

## Preview deployment and recovery

Only `web/dist` is uploaded by the separate web workflow. Android CI is retained. After deployment, check both base URLs, logo/font/data requests and a synthetic workout; never reset a real user's browser store. If startup, local saving or asset paths regress, stop further deployments and revert the website commit through a normal reviewed Git revert (no history rewrite). Keep exported data before testing an older app against existing IndexedDB; database downgrades are not an automatic rollback strategy.

## Device checklist

Use a new test browser profile with synthetic plans. Never clear the owner's existing app or browser storage.

- Onboard from scratch, select a theme and plan, restart, verify zero rewards until eligible proof.
- Import a noted plan, create a custom exercise, swap session-only then plan-level; check names/tracking/notes after resume.
- Log/edit/delete sets and effort; queue warmups, lock/unlock, skip/log explicitly; inspect 65kg barbell view and lb conversion.
- Finish once, verify History/Ledger/recovery; delete/edit retained sets and check recomputation.
- Download ZIP with photos, restore into a separate test context, compare actual images and data. Try malformed files.
- Test independent tabs and a stale restore preview while the other tab starts a workout.
- Install to Home Screen, compare storage explicitly, export before switching; test network-off startup and long suspension.
- Test 200% text, landscape, VoiceOver, hardware keyboard and iPhone keyboard sheets.
- Offer an update while training/saving; no forced reload or lost draft. Only update after explicit safe action.
