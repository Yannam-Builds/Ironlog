# Native spacing and Stats AI implementation plan

> Execute inline in the existing native workspace; preserve all unrelated changes. The user approved direct implementation without a visual preview.

**Goal:** Compact, equal two-line workout pills; independent layout spacing; Stats cloud summaries only in cloud mode.

**Architecture:** Reuse the revision-safe SpacingStore separately for card, content-padding and text/control spacing. Fall back to the existing spacing preference for migration. Route spacing through composition locals without changing density, fonts, touch targets, safe areas or body-map geometry. Scope cloud summary state and coroutine lifetime to an enabled, configuration-keyed composition.

**Tech stack:** Kotlin, Compose, SharedPreferences, coroutines, JUnit and Android Compose instrumentation.

## Tasks

- [x] Change HomeWorkoutPillLayoutTest to expect no third row. Run on the emulator and observe failure, then remove the badge row and pulse, use two one-line labels and a static border/fill with accessibility recommendation semantics. Test default and 200% fonts with scrolling.
- [x] Add regression contracts for independent spacing controls and Stats mode gating. Run `:app:testDebugUnitTest --tests '*SpacingRolesContractTest'` before implementation.
- [x] Add semantic spacing roles in SpacingSettings/AppSpacing; preserve revision-safe save/rollback and legacy preferences. Provide all roles in IronLogApp. Add three labelled sliders and Compact/Balanced/Roomy actions with a live preview and reset in SpacingSettingsCard.
- [x] Route screen-level list gaps through the card role; keep nested content gaps in the text/control role and audited padding in the padding role. Review primary Home/Plans/Log/Stats/Settings layouts. Do not rescale diagrams or minimum touch areas.
- [x] Isolate CloudStatsSummaryHost and gate Stats by `intelligenceMode == "cloud_ai"` plus valid configuration. Key results by configuration and summary inputs; cancel/dispose work when disabled. Do not erase saved credentials. Test disable/re-enable and cancellation using synthetic loaders, never real API keys. Include credential-only refresh.
- [x] Run spacing persistence/migration/isolation tests, full JVM suite, debug lint, debug and instrumentation builds. Install QA only on the emulator, run focused Compose tests and inspect screenshots. Do not update the phone or publish.
- [x] Record verification and remaining visual limitations in appearance documentation.

The user's added intelligence request is recorded in `docs/program-intelligence-audit-2026-08-31.md`.
Integrated result: 247 JVM tests, debug lint and both debug builds pass. Six focused emulator tests pass,
including the bundled-font resource test. The full all-theme/all-screen acceptance matrix remains out
of this bounded verification pass.

## Spacing reference

OpenGym `frontend/src/index.css` at `aac9f377d5edfd68b589a392724028ecc7a1502f` uses 16px page padding, 14px card radii and compact type hierarchy. Adopt its restrained grouping principles independently; retain native IronLog themes and artwork. No OpenGym source or media is copied.
