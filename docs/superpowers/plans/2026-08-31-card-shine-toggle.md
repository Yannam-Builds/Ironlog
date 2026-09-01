# Card shine restoration implementation plan

**Approved direction:** Restore the original moving card shine, keep compact equal-sized pills,
and add an Appearance on/off toggle. Continue directly without another visual preview.

**Architecture:** Keep the existing five-second diagonal brush and card geometry. A small Boolean
appearance store defaults on and publishes only successful disk writes. The root provides its value;
the card skips the animated brush entirely when off. Use the existing process-owned save queue so
navigation does not cancel writes. No workout data, theme selection or other animations are changed.

**Files:** `ui/CardGradient.kt`, `ui/screens/home/HomeScreen.kt`, `ui/IronLogApp.kt`,
`ui/theme/CardShineSettings.kt`, `ui/theme/CardShineRuntime.kt`,
`ui/screens/settings/CardShineSettingsCard.kt`, `ui/screens/settings/SettingsScreen.kt`.

- [x] Add a failing source-integration regression for card shine and Settings wiring; run
  `:app:testDebugUnitTest --tests '*CardShineContractTest'` and confirm missing integration assertions.
- [x] Add store tests for default-on, malformed input, on/off reload, failed writes and ordered updates.
- [x] Implement `CardShineStore.update(Boolean)` with write-before-publish behavior and SharedPreferences
  key `animated_card_shine` in `ironlog_typography`. Preserve prior state on a failed commit and surface
  a retryable error in Settings.
- [x] Restore `.animatedCardShine(c.accent)` after the opaque card base. `LocalCardShineEnabled` controls
  whether the existing animated brush is composed; no border pulse, text or dimension changes.
- [x] Add a labelled, 48dp-minimum switch row under Appearance. Persist via `AppearancePersistence`
  and collect the same process store at the app root before Home is displayed.
- [x] Run full JVM/lint/debug builds and Compose pixel tests: enabled frames change, disabled frames
  remain identical, toggling leaves card bounds unchanged. Verify the actual preference survives reload.
- [x] Build a new signed release, check signature/fixture exclusion and save a separately named APK
  to Downloads. Do not overwrite the earlier download, publish, or install on the physical phone.

Existing dirty workspace changes are preserved; no commit or branch manipulation is part of this pass.

Result: 254 JVM tests, four focused emulator tests, debug/release lint, debug/instrumentation/release
builds pass. Release v6 (`0.1.0-pre-alpha.5`) verified with the existing signer, non-debuggable manifest,
no QA fixture/private files or QA markers, and a successful in-place emulator update/startup. Recent
AndroidRuntime log window was empty. Previous install timestamp was preserved. Physical phone untouched.
Downloads APK: `Downloads/IronLog-0.1.0-pre-alpha.5-release.apk`.
SHA-256: `F52F7E259CD34FA775830C8BBB05AB1BC9AFB4539C18FC630AF72CEC20F462DA`.
