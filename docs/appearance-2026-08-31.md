# Native appearance settings — 2026-08-31

Settings → Appearance now includes typography and three independent layout-spacing controls. Defaults remain Lexend, Original weights, and 100% spacing.

## Controls and persistence

- Typography: Lexend plus 20 bundled upright variable fonts; Original, Light, Regular, and Bold presets; live sample, scrollable font picker, reset, and offline font licenses. Real `wght` instances are used and weights clamp to each font's verified axis range; synthetic bold is disabled.
- Spacing: Between cards, Content padding, and Text & controls each have an 85–125% slider in 5% steps. Compact (85%), Balanced (100%), Roomy (125%), a live two-card preview and reset affect all three. Each slider announces its own name/value and retains a 48dp minimum interaction height.
- SharedPreferences file: `ironlog_typography`; keys: `font_id` (string, default `lexend`), `preset_id` (string, default `original`), `spacing_cards`, `spacing_padding`, `spacing_content` (floats). An unset role inherits legacy `spacing_scale` or `1.0`; editing one role does not overwrite the others. Invalid values normalize safely. These local appearance preferences are not included in the ObjectBox-backed explicit backup format.
- Preferences load before visible app content. A process-owned ordered save queue survives navigation away from Settings. Font field patches read the latest selection inside the serialized update and publish only after successful IO `commit()`.
- Spacing previews do not write to disk. Release captures a revision-tagged request before queueing. Failed writes restore the last persisted value only if that exact request remains current, preserving newer A→B→A drag sequences. Preview locks never cover disk IO. Save errors are visible and retryable.

## Source ownership and coverage

Core files under `app/src/main/java/com/ironlog/app/`:

- `ui/theme/TypographySettings.kt`, `TypographyStore.kt`, `TypographyRuntime.kt`: registry, normalization, state and persistence.
- `ui/theme/IronLogFontFamilies.kt`, `IronLogTypography.kt`, `IronLogText.kt`, `TypographyPaint.kt`: resource mappings, all 15 Material styles, explicit-weight text boundary, native Paint.
- `ui/theme/SpacingSettings.kt`, `AppSpacing.kt`, `AppearanceSaveQueue.kt`: bounded scaling, revisioned requests, padding/arrangement helpers, ordered application-lifetime saves.
- `ui/IronLogApp.kt`: reactive typography and spacing providers.
- `ui/screens/settings/TypographySettingsCard.kt`, `SpacingSettingsCard.kt`, `SettingsScreen.kt`: accessible controls, previews, error handling and Appearance entry points.
- `HealthConnectRationaleActivity.kt`, `services/ShareService.kt`: app-owned native text and exported-card typography.

Coverage audit: 1,282 `Text` call sites across 54 files use the preference-aware boundary. Custom editor styles and Body Weight/Volume Analytics Canvas text are included. Spacing reaches 403 arrangement calls, 225 padding calls, and 185 explicit Spacer gaps. Existing screen logic and unrelated dirty-worktree changes were preserved.

The refinement routes main-list gaps in 22 screens through the card role, plus Body Weight summary
grid/BMI spacing. Nested content and existing padding use their respective roles. Home's workout
pills now have two one-line labels, equal dimensions, static recommendation highlight and accessible
recommendation state; there is no third badge row or pulsing border. The surrounding card has a
single header/play control, a two-line plan title, restrained outline and smaller padding/gaps.

### Card shine restoration

The original diagonal moving highlight is restored on the Home workout card, without restoring the
pulsing pill border or third line. Settings → Appearance → **Animated card shine** defaults on and
persists `animated_card_shine` as a Boolean in `ironlog_typography`. This preference is local appearance
state, not workout data, and is not included in ObjectBox backups. It affects this decorative shine only.

The root observes the preference before rendering Home. Off removes the animated brush's composition
entirely; it does not just hide a still-running effect. Writes use the process-owned appearance queue on
IO; state changes publish after a successful commit. A failed save leaves the previous value selected
and displays a retryable error. The whole labelled switch row is accessible and has a 64dp minimum
height; no font, card size or pill spacing changes are part of this restoration.

Verification for this follow-up: 254 JVM tests pass; debug lint and debug/instrumentation builds pass.
Four API 36.1 emulator tests pass (7.737s): pixel changes on/restart, identical frames off, unchanged card
bounds, real SharedPreferences reload, plus both pill-layout regressions including 200% text. Manual
Appearance on/off/on taps were also checked through accessibility state, and local screenshots inspected:
`artifacts/shine-home-restored.png`, `artifacts/shine-settings-on.png`. Existing compiler/lint warnings and
ObjectBox test cleanup diagnostics remain; no claim of a warning-free build or complete device matrix.

The restoration is included in signed release `0.1.0-pre-alpha.5` (version code 6), saved separately
in Downloads. Release lint passed with zero errors (157 warnings, 22 hints). Signature and QA-fixture
exclusion checks passed, followed by an in-place emulator update/startup with no AndroidRuntime errors
in the recent checked window. The physical phone and previous downloaded APK were not modified.

Stats cloud summaries now follow the canonical `cloud_ai` setting. Switching to built-in disposes
the request and old result without deleting credentials. Provider/configuration/input changes restart
the keyed request; an observable credential revision also handles API-key-only saves.

### Compact set controls and completion debrief

Logged RPE/RIR now use small tap-to-edit chips rather than persistent, full-height text fields.
The normal working-set marker no longer has a large filled tile; special set types keep a small
tinted badge. All controls retain 48dp targets, and effort/action groups wrap independently on
narrow screens with large text. Effort editors are opaque, labelled dialogs: Save validates the
value, blank clears it, and Cancel makes no change. RIR accepts integers 0–10; RPE accepts 1–10,
including decimals. Typing alone does not persist a partial effort value.

Workout completion now receives the selected intelligence mode and shares the keyed cloud-request
lifecycle with Stats. Only configured `cloud_ai` renders or requests a debrief. Built-in modes
render nothing and make no request even when credentials remain saved. Switching mode cancels work;
changing credentials/configuration discards old results. An empty response has an explanation and
Retry rather than an empty AI card. Credentials are not deleted by switching modes.

Regression evidence: two JVM wiring checks and three initial Compose set-row checks failed before
the fixes. The integrated JVM suite then passed all 258 tests. Fourteen API 36.1 Compose tests passed
in 26.47s, covering compact geometry, 200% font touch-target separation, save/clear/cancel/invalid
effort, built-in zero requests, cancellation/re-enable, blank-response retry, existing Stats behavior,
card shine and equal-height carousel pills. Cloud loaders used synthetic results, never external APIs.
Inspected screenshots: `artifacts/compact-set-normal.png` and `artifacts/compact-set-large-text.png`.
These are synthetic component captures, not proof of an exhaustive screen/device matrix.

Manual QA also logged three weighted pull-up sets, changed set 1 to RIR 2, reopened the editor to
verify it saved, and opened the real built-in completion sheet with no AI card. Local screenshots:
`artifacts/compact-workout.png`, `artifacts/compact-effort-editor.png`, and
`artifacts/compact-summary-builtin.png`. Effort tracking was returned to its prior Off setting.
The emulator went offline/exited during cleanup of this new QA-only session; discard completion was
not confirmed. No existing history or release-app workout was deleted, and the physical phone was
not connected or modified.

Final command reran the full 258-test JVM suite, debug/release lint and signed minified release build:
all passed (3m 33s). Release lint has zero errors, 158 warnings and 22 hints; the additional shared
cloud-host naming warning and existing ObjectBox cleanup diagnostics remain documented limitations.
Release `0.1.0-pre-alpha.6`, code 7, is saved separately as
`Downloads/IronLog-0.1.0-pre-alpha.6-release.apk` (51,320,691 bytes).
SHA-256: `7D1860FB85BC792CA7D6AD3A7F95ECCA68606D0351E1964C50631C3280EBEFAE`.
Copy hash, IronLog signature, non-debuggable metadata and exclusion of QA fixture/identity markers,
keystores, local properties and databases were verified. Emulator checks used the debug build of the
same source; the final signed APK was not installed after the emulator exited and still needs release
runtime/physical-device verification. No Git publishing or previous downloaded APK replacement.

## Deliberate limits

- No density overrides, text-size changes, icon scaling, touch-target-size changes, or changes to artwork.
- Body-map and Recovery viewport files, drawing geometry, fixed geometry, audited interactive-control padding, large safe-area spacer heights, and `PaddingValues`/system insets are excluded from spacing scaling.
- Launcher/Glance widgets, Android notifications, the keyboard, and OS permission/file dialogs retain platform-controlled typography. Native rationale/export-card layout spacing remains fixed.
- Emulator QA at approximately 320dp width and 200% system text size exposed existing bottom-navigation label clipping (not introduced or changed here). The spacing endpoint-label issue found in the same pass was corrected with an adaptive FlowRow.

## Verification and delivery

JVM coverage includes defaults, invalid storage, all font IDs/ranges, all Material roles, immediate/fresh-store state, queued font-field changes with delayed storage, failed saves, bounded spacing, preview/release persistence, revision-protected A→B→A failure, responsive label structure, and source coverage guards.

Resource-only instrumentation class `com.ironlog.app.ui.BundledFontInstrumentedTest` loads all 21 families and measures 252 family/preset/weight combinations without launching MainActivity or changing preferences/workout data. The coordinating task ran this class successfully on the emulator.

Build command: `./gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon`.

Latest integrated verification passed in 1m 49s: 247 JVM tests across 53 suites, with zero failures,
errors or skips. This includes the parallel program-intelligence refinement. Lint reports zero errors,
158 warnings and 22 hints. Explicit SharedPreferences commits are retained so failed-write results can
be checked. Existing ObjectBox transaction-cleanup diagnostics and compiler/deprecation warnings remain;
the build is not warning-free and those ObjectBox diagnostics warrant separate investigation.

Focused Compose tests passed on API 36.1: two pill-layout/scroll tests (including 200% fonts), plus
three cloud lifecycle/credential tests using synthetic keys and no cloud requests. Manual Settings QA
confirmed Balanced sets all roles to 100%, then changing only Between cards to 85% leaves the other
two at 100%. Screenshot inspection: `artifacts/spacing-home-final.png` and
`artifacts/spacing-settings-independent.png` (private local QA artifacts, not for publication).

Final APK rerun: all six instrumentation tests passed in 8.043s, including bundled-font validation.
Home, Plans, History, Stats and Training Intelligence navigation smoke checks completed. The recent
1,000-entry logcat window returned no AndroidRuntime error entries. Additional private screenshots:
`artifacts/spacing-stats-final.png` and `artifacts/intelligence-final.png`.
APK SHA-256: `532999814409A14CA0D753292002FE081B26710B90BB4219430CC8A1C48ECA80`.

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`. Instrumentation APK: `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`. Reports: `app/build/reports/tests/testDebugUnitTest/index.html` and `app/build/reports/lint-results-debug.html`.

Debug and instrumentation APKs rebuilt and installed with `adb install -r` on the emulator only.
`git -c core.safecrlf=false diff --check` passes. A complete all-screen/theme/device matrix was not
performed in this pass; emulator evidence does not replace physical-device testing.

No native Git commit, release build, physical-phone installation, uninstall, or app-data clearing was performed. Existing user changes remain uncommitted and intact.
