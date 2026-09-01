# Compact logged sets and mode-scoped completion debrief

Requested correction: RPE/RIR and set-type boxes are too visually heavy, and the completion summary
shows a cloud debrief even with built-in intelligence selected. Preserve existing unrelated work.

- [x] Reproduce summary mode/credential wiring failures in JVM tests and oversized always-visible
  effort text fields in Compose tests before changing production code.
- [x] Replace permanent RPE/RIR text fields with quiet inline tap-to-edit chips. Keep 48dp interaction
  targets; open a labelled, opaque editor only on demand, validate input on confirmation and allow
  clearing. Wrap the effort/action groups at narrow widths or large fonts. Keep a smaller visual
  set-type badge inside its 48dp touch target; preserve existing type cycling and set actions.
- [x] Share the already-tested keyed cloud-request lifecycle between Stats and workout completion.
  Gate the completion debrief by canonical `cloud_ai` plus configured credentials; cancellation and
  configuration changes discard old results. Observe key-only updates. Missing/blank responses show
  an explanatory fallback with retry, never an empty card. Built-in mode renders and requests nothing.
- [x] Verify normal/large-text compact set geometry, effort edit/save/clear/cancel, configured built-in
  zero calls, cloud cancellation/re-enable, blank response retry and existing Stats lifecycle tests.
- [x] Run full JVM suite, lint, debug/instrumentation builds and emulator tests/screenshots. Build and
  verify a separately named signed release APK for Downloads; no phone update or publishing.

Only local synthetic fixtures are used for cloud tests; no external API calls or user credentials.

Evidence: 258 JVM tests, 14 Compose tests, debug/release lint and signed APK build passed.
Signature and QA-fixture exclusion checks passed; pre-alpha.6 is saved in Downloads. Manual QA
screenshots confirm three compact sets, RIR editing and a built-in completion sheet without AI.
The emulator exited before final signed-release runtime verification/QA-session cleanup; those are
explicitly unverified. See `docs/appearance-2026-08-31.md` for artifacts and limits.
