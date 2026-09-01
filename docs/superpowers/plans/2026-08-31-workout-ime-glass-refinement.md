# Workout keyboard stability and glass optics refinement

> **For agentic workers:** Use the test-driven implementation and verification workflows. Preserve the existing dirty Android workspace. No publication, phone update, or unrelated audit fix is authorized by this pass.

**Goal:** Keep active-workout input stable across set insertion and keyboard changes, and make the existing optional navigation glass clearer and visibly refractive.

**Architecture:** One consumption-aware workout viewport owns IME space; focused input relocation remains within the LazyColumn. Navigation keeps its existing layout and hit targets. A bounded, oversized backdrop texture supplies real surrounding pixels to the optical shader; only the material layer is refracted. The wider app/OpenGym comparison is read-only.

**Tech stack:** Android Jetpack Compose, ObjectBox, Haze 1.6.8, API33+ AGSL, JVM and Compose instrumentation.

## Scope and constraints

- Keep the 64dp bar, five equal tab slots, 21dp icons, labels, outer margins and solid-off appearance unchanged.
- Keep glass and card-shine preferences independent. Preserve platform motion-scale behavior and opaque unsupported-device fallback.
- The user's explicit glass request is a scoped exception to the older brand document's generic glass prohibition; menus/dialogs remain opaque.
- Do not infer Apple's proprietary renderer is available on Android. Kotlin's linked article uses native iOS navigation; this is an Android approximation.
- Do not change workout persistence, supersets, notes or warmup semantics to solve a layout issue.

## 1. Reproduce and establish failing checks

- [x] Capture real first-set logging with numeric keyboard on API36.1. Header and set insertion move the focused input down; current root ignores IME insets.
- [x] Extract the existing root layout into `ActiveWorkoutViewport.kt` without changing its behavior.
- [x] Add `ActiveWorkoutImeContractTest.kt`, and run it before the fix. Its inset-consumption assertion fails.
- [x] Add `ActiveWorkoutImeLayoutTest.kt` using the production viewport, a real focused field, actual SetRow, stable LazyColumn key, changing keyboard insets and rest overlay. Include already-consumed inset coverage.
- [x] Add `LiquidGlassOpticsTest.kt`: real fine-stripe backdrop must remain visible in the center; a color band outside the capsule must be visibly pulled into its curved rim.
- [x] Run the new behavioral tests against the unchanged renderer/viewport and record their failures.

## 2. Fix the workout viewport

Files: `ui/screens/workout/ActiveWorkoutViewport.kt`, `ActiveWorkoutScreen.kt`, associated tests.

- [x] Consume IME insets exactly once using `Modifier.windowInsetsPadding(imeInsets)` before measuring the list and rest footer.
- [x] Replay first-set and subsequent-set insertion. Keep focus; never dismiss the keyboard as a substitute for correct layout.
- [x] Preserve the focused input's visual position across content growth through list-local relocation. Await framework relocation and cancel actual drag/fling or focus-transfer requests. Test it before adding it.
- [x] Check first/second sets, deletion, IME open/close and nested insets with the production viewport. Measure footer height; do not add arbitrary screen offsets.
- [x] Check a delayed originating-set save after superset navigation without pulling the screen back to the old focused field. Established RED with retained source focus, added a pre-change visibility guard, and reran the same assertion GREEN.
- [ ] Complete the full active-workout compact/landscape/large-font keyboard matrix. Existing compact set-row and navigation geometry tests are not a substitute for this entire journey.

## 3. Improve glass optics

Files: `navigation/LiquidGlassRenderer.kt`, optical geometry/helper if needed, `ui/LiquidGlassOpticsTest.kt`.

- [x] Replace the 14dp blur with a small full-resolution blur so the backdrop remains recognizable.
- [x] Capture a bounded overscan around the same 64dp visible bar; include sufficient sampling margin for rim distortion.
- [x] Refract along curved capsule normals, including pixels beyond the visible bar. The center remains comparatively undistorted.
- [x] Derive broad edge shading/highlights from the same curved profile instead of painting an ordinary bright outline.
- [x] Keep icons/labels outside the shader and preserve the moving selected lens, off-state pixels, unavailable/failed-effect fallback and idle frame behavior. Pair rendered background samples with quantized foreground colors across all themes to check contrast.

## 4. Read-only app/OpenGym audit

- [x] Inspect current UI, input/units, persistence, backup/restore, recovery/intelligence, Ledger, widgets, performance and accessibility paths.
- [x] Compare exact OpenGym revision `75fb168a03de09f995d05efd4fd2bfda2d595e0f`; separate source evidence from runtime validation and README claims.
- [x] Report confirmed defects separately from design choices, hypotheses and opportunities. Avoid copying OpenGym source/media.
- [x] Save prioritized evidence, source anchors and suggested acceptance tests in `docs/reviews/2026-08-31-ironlog-app-audit.md` and `docs/reviews/2026-08-31-opengym-reference.md`.

## 5. Verification and handoff

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon
```

Use explicit version properties when installing an emulator APK, so the local default version does not downgrade a newer build. Run instrumentation against `com.ironlogpro.app.debug.test/androidx.test.runner.AndroidJUnitRunner` and inspect test results, not only adb's exit code.

- [x] Run existing glass, preference, compact set, cloud gating, carousel and opacity tests as regressions.
- [x] Check real screenshots and gestures; confirm no continuous idle redraw or fatal logcat entry in the verified emulator paths.
- [x] Document coverage and remaining device/performance limitations. No physical-device claim without actual phone verification.

### Verified handoff — 1 September 2026

- 289 JVM tests passed; debug lint: zero errors, 161 warnings, 22 hints.
- Final instrumentation package: 38 normal-motion cases passed, with its one motion-disabled case run separately and passed (39 distinct passing cases). The emulator animation scale was restored to one.
- Actual QA keyboard replay covered first/second logs, deletion, reopen, drag, process restart and superset advance. Pixel tests confirmed genuine outside-edge sampling, center clarity, all-theme contrast, stable navigation dimensions, opaque fallback and settled idle rendering.
- An intermittent card-shine test was synchronized with actual Compose/provider application and save completion; no production shine change was made.
- The full active-workout landscape/large-font/compact-height matrix remains explicitly outside this completed bounded pass, as do physical-device/TalkBack and release-performance certification. See the main audit's verification section for precise coverage.
- No phone changes, data clear, release publication, Git push, website changes or unrelated audit fixes.

Sources: [Kotlin's native iOS integration](https://kotlinlang.org/docs/multiplatform/ios-liquid-glass.html), [Apple's optical design overview](https://developer.apple.com/videos/play/wwdc2025/219/), [Android AGSL](https://developer.android.com/develop/ui/views/graphics/agsl/using-agsl).
