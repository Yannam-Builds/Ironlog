# Liquid Glass Navigation Implementation Plan

> **For agentic workers:** Use superpowers:subagent-driven-development for the independent preference task; keep renderer integration in the coordinating task.

**Goal:** Add toggleable Android glass rendering and fluid selection motion without changing navigation layout or behavior.

**Architecture:** Keep the existing Haze backdrop source. Render blur and an Android 13+ AGSL refractive capsule in a background-only layer, with clear icons/labels above it. Older/unsupported devices get a readable tinted fallback. A separate persisted Boolean controls the effect; disabling it restores the original solid bar and removes backdrop capture.

**Tech Stack:** Existing Jetpack Compose, Haze 1.6.8 (patching the 1.6.7 startup redraw bug), platform RuntimeShader/RenderEffect, SharedPreferences and the existing ordered appearance-save queue.

## Approved constraints

- Native Android approximation, not Apple's private iOS renderer.
- Keep 64dp bar height, 16dp side/12dp bottom offsets, 21dp icons, existing label styles, five equal tab slots and 5dp lens insets. Do not scale icons or touch targets.
- Preserve all five routes, pager behavior, selected tint and resumable-workout control.
- Enable by default, with Settings → Appearance → Liquid glass navigation. Independent of Animated card shine.
- Animate only selection/press changes, respect system animation duration scale, and do not create perpetual decorative animation or sensor listeners.
- Preserve the current dirty feature branch. Moving to a clean worktree would omit the user's uncommitted app baseline; no stash/reset/commit/push or phone update.

## Task 1 — Preference, runtime and settings

Files: `ui/theme/LiquidGlassSettings.kt`, `LiquidGlassRuntime.kt`, `ui/screens/settings/LiquidGlassSettingsCard.kt`, `ui/IronLogApp.kt`, `ui/screens/settings/SettingsScreen.kt`.

- [x] Add JVM tests for default-on, invalid value fallback, persisted off/reload, no-op saves and failed write leaving observed state unchanged; run before implementation.
- [x] Implement `LiquidGlassStore(storage: LiquidGlassStorage)` with `enabled: StateFlow<Boolean>` and synchronized `update(Boolean)` that writes before publishing. Key: `liquid_glass_navigation` in `ironlog_typography`.
- [x] Mirror existing application-owned appearance queue and retryable save errors. Provide `LocalLiquidGlassEnabled` at app root. Add a 64dp-minimum labelled Switch row next to card shine; no layout controls are removed.
- [x] Run focused tests and review persistence/independence before integration.

## Task 2 — Background renderer and unchanged tab layout

Files: `navigation/IronLogTabBar.kt`, `navigation/LiquidGlassRenderer.kt`, `navigation/LiquidGlassGeometry.kt`; move only the existing tab bar out of `AppNavigator.kt` for direct Compose testing.

- [x] Record baseline dimensions and tab icon/label bounds with an instrumentation fixture. Add a failing contract test for a preference-controlled glass layer, before implementing the effect.
- [x] Keep original bar rendering in the disabled branch. Enable Haze capture only when glass is enabled. Add a clipped background layer with HazeStyle (theme tint, 14dp blur, no noise) and original capsule shape.
- [x] Add Android 13+ refraction using `RenderEffect.createRuntimeShaderEffect(shader, "content")` on that background only. Use measured size and animated lens coordinates as uniforms. Warp background near capsule edges, draw curved specular rims and a selection lens; never shader-filter icons or labels. Catch shader creation failure and use the fallback renderer.
- [x] Use the existing selection spring, clamp lens geometry inside the bar and allow a small drawing-only directional stretch during travel. No measurement changes. System-disabled animations settle immediately through Compose's animation clock.
- [x] Add selected-tab semantics without altering gesture handling. Keep icons/labels crisp for dark and light palettes.

## Task 3 — Verification and delivery

- [x] JVM tests: geometry/clamping, API guard contract and preference failures. Full suite: 271 tests, zero failures/errors. Debug lint: zero errors (161 existing warnings, 22 hints). Debug, instrumentation and signed minified release builds pass.
- [x] Compose tests: enabled/off identical bar/tab/icon bounds at 320/411dp, all themes, 200% font, selection transitions and settled frames, no idle redraw loop, toggle persistence, disabled-layer removal, underlying taps blocked, reduced-motion state and forced no-blur fallback. Normal run reports `OK (24 tests)` with the disabled-motion test assumption-skipped; a separate scale-zero run reports `OK (1 test)`, covering all 24 distinct tests. Includes the 14 prior effort/debrief/card-shine/carousel regressions.
- [x] Emulator screenshots: patterned backdrop and actual Home/Settings, on/off, first/last tab and mid-transition. Reviewed opaque fallback, legibility, effect presence and unchanged layout. The off preference persisted across force-stop/relaunch. Actual idle Settings frame count stayed at 1700 across successive observations. No real cloud calls.
- [x] Built a separately named signed release APK, verified signature/fixture exclusion, and saved to Downloads. No phone installation or publishing.

Sources checked: Kotlin's iOS Liquid Glass guide, Android AGSL documentation, and Haze 1.6.7 source. The existing native screen structure remains authoritative.

## Runtime investigation

Initial on-at-startup Compose tests exposed sustained redraws with Haze 1.6.7: assertions and screenshots completed, but ActivityScenario cleanup waited indefinitely for native UI idleness. Disabling AGSL did not remove the loop. Gfxinfo also showed continuously increasing frames in the actual QA app without input. Haze's [upstream fix #725](https://github.com/chrisbanes/haze/pull/725), shipped in [1.6.8](https://github.com/chrisbanes/haze/releases/tag/1.6.8), rechecks source/effect window IDs in the pre-draw callback instead of retaining a stale startup decision. The dependency is patched rather than hiding the loop with a test cleanup workaround. Verification below must include the original on-at-startup test and actual idle rendering.

The 1.6.8 rerun passed the original test and a native draw-count regression. Temporary shader bypass and diagnostic threads were removed. Pixel comparisons exclude the intentionally transparent corners outside the capsule. The test recomposer receives the platform motion scale explicitly via a live getter; initialization can precede Android's animation-scale cache refresh. After scale-zero testing, restore the emulator to explicit `animator_duration_scale=1`; deleting the unset key alone left the window manager's cached scale at zero.

## Delivery and limits

- APK: `Downloads/IronLog-0.1.0-pre-alpha.7-release.apk` (version code 8, 51,320,691 bytes).
- SHA-256: `EB46728C5AC73C3CAF4CEE047B6D4951635DA23B26553294FBC892CEA6CDD321`; copied file matches build output.
- APK Signature Scheme v2 verified; existing IronLog signer SHA-256 `93970f38b16f3a10e958cdd1339b6f2ca0616cef65595fc53f1d1123a373bba7`.
- APK archive excludes QA fixture/backup/database/keystore/local.properties entries; DEX contains no `QA Athlete` marker.
- Runtime screenshots remain local under ignored `artifacts/glass-*.png`.
- Signed release upgraded the API 36.1 emulator from code 3 to code 8 with `adb install -r`. `firstInstallTime=2026-07-28 13:50:30` was preserved; `lastUpdateTime=2026-08-31 22:00:38`. MainActivity resumed with no AndroidRuntime fatal entry, and the existing active-workout resume control remained visible. QA glass preference was restored to on after testing.
- API 36.1 emulator verified. Forced no-blur rendering is tested, but actual API 26–32 devices and physical-device GPU behavior were not tested. This is an Android interpretation, not Apple's native renderer.
- Preexisting ObjectBox read-transaction cleanup warnings remain in JVM test output; no claim of a warning-free whole codebase audit.
- Preserve the dirty branch and all unrelated user changes. No commit, push or history rewrite.
