# Shared Typography Implementation Plan

> **For agentic workers:** Use subagent-driven-development for the isolated Kotlin task and review stages; use test-driven development for each platform.

**Goal:** Give the website, embedded/full-screen web app, and Kotlin app Lexend plus twenty locally bundled font families, selectable app-wide with Light, Regular and Bold presets.

**Architecture:** A pinned Google Fonts source registry records IDs, files, weight ranges, hashes and licenses. Web typography is an independent persisted external store, shared across tabs/iframe and applied through CSS tokens. Native typography uses a persisted root-scoped configuration, Compose font families and centralized text-weight mapping. No workout schema changes, accounts or network font loading.

**Tech Stack:** React/TypeScript/CSS, local variable TTFs, Kotlin/Compose, existing settings persistence, Vitest/Playwright/JVM tests.

## Approved design and scope

The user approved both web and Kotlin implementation after the proposed Appearance → Typography picker, live previews, twenty additional fonts and Light/Regular/Bold styles. Keep existing default styling until the user explicitly selects a preset. Label Light as the landing-page look. Cover screens, menus, dialogs, inputs and in-app charts; preserve icon fonts/artwork and document platform-controlled text. Do not alter training data. Keep the Android dirty workspace intact and never publish unrelated native changes or update the physical phone.

Candidate families: Lexend (existing), Inter, Manrope, DM Sans, Plus Jakarta Sans, Outfit, Sora, Urbanist, Nunito, Nunito Sans, Rubik, Work Sans, Public Sans, Figtree, Assistant, Mulish, Quicksand, Raleway, Montserrat, Exo 2, Source Sans 3. Verify each upright variable file and license before accepting it. Preserve original source font files and license text; only subset web assets if provenance records that transformation.

## 1. Source and license registry

- [x] Resolve and record the Google Fonts commit and each family directory through GitHub's API.
- [x] Download only upright TTF files and OFL.txt, inspect weight axes, record SHA-256 and byte counts.
- [x] Place web files under `web/public/fonts/`, native files under `app/src/main/res/font/`; retain licenses on both platforms.
- [x] Add a registry test requiring exactly 21 unique IDs, valid ranges and a real bundled resource for every entry.

## 2. Web typography

Files: create `web/src/ui/typography.ts`, `web/src/ui/FontPicker.tsx`, `web/src/typography.css`, `web/tests/typography.test.ts`; modify `web/src/main.tsx`, `web/src/landing.tsx`, `web/src/styles.css`, `web/src/landing.css`, `web/src/features/Settings.tsx`, `web/vite.config.ts`.

- [x] Test unknown IDs/presets fall back safely, selection persists, storage denial is surfaced, and external changes update the subscriber.
- [x] Run `npm test -- typography` before implementation and confirm the missing behavior fails.
- [x] Implement validated `{family, preset}` storage with `lexend`/`original` defaults and CSS variables for body, label and heading weights. Explicit Light uses body 350, controls 450, headings 450; Regular uses 400/500/600; Bold uses 500/650/750, clamped to each font's supported range.
- [x] Use one accessible picker in Settings and the landing appearance section, showing a selected-font preview of workout text/numerals. Do not download all fonts merely to render the picker list.
- [x] Initialize the preference before rendering both entries; listen for cross-tab and iframe storage events without storage-event feedback loops. Keep native-theme selection independent.
- [x] Include selected fonts in offline support with content revisions. Test missing font fallback and preserve usable UI while fonts load.

## 3. Kotlin typography

Files: `ui/theme/IronLogTypography.kt`, new typography registry/runtime/preferences and picker files, `ui/IronLogApp.kt`, `ui/screens/settings/SettingsScreen.kt`, affected centralized text wrappers and tests under `app/src/test`/`app/src/androidTest`.

- [x] Write failing JVM tests for registry/preset normalization, range clamping and invalid stored preference recovery.
- [x] Implement persistent selection in existing settings or isolated SharedPreferences, never requiring ObjectBox schema changes; load before screen rendering and update the root immediately after a successful save.
- [x] Map all Material text styles to the chosen family and preset, including explicit screen weights through a centralized wrapper if needed. Audit custom Canvas/Paint text separately; never replace icon glyphs.
- [x] Add a scrollable opaque picker with family names, preview, selected state and weight preset. Preserve all pre-existing dirty edits.
- [x] Run focused tests, complete JVM tests/lint/build and emulator checks where available. No phone install/uninstall/clear and no release publication in this task.

## 4. Verification, review and publication

- [x] Browser tests: all 21 families load, persisted preference across landing/iframe/full-screen, invalid storage, all presets, 320px/large text, menus/inputs and complete workout after switching; offline reload retains the choice.
- [x] Run `npm test`, `npm run build`, `npm run verify:output`, complete Playwright suite and separate origin-stopped tests. Check screenshots and font payload totals.
- [x] Independent spec review followed by code-quality review; resolve findings and rerun affected gates.
- [x] Update README and font acknowledgments. Publish only website-scoped changes via existing verified GitHub Pages workflow; native changes stay separate from unrelated Android work.
- [x] Verify the live website. Report native build/emulator evidence separately from physical-device testing and distinguish bundled font choices from platform-controlled text.

## Added scope: global UI spacing

The user additionally requested a Settings slider to adjust spacing everywhere, on both platforms. Implement 85–125% in 5% steps, 100% default and a reset. Scale layout padding/gaps without changing text, icons, touch-target minimums, safe-area insets or chart/body-map geometry. Persist independently of training data; apply across screen navigation and process reload. Verify bounds, invalid settings, write failures, compact/spacious extremes, 320px/large text, responsive controls and safe dismissal.

## Delivery evidence

- Website implementation `51e5a61` deployed successfully through [GitHub Pages run 33378594968](https://github.com/Yannam-Builds/Ironlog/actions/runs/33378594968): 94 unit tests, 49 browser tests and 2 origin-stopped workout tests passed, alongside build/output checks.
- Nine follow-up tests against the public GitHub Pages URL passed in Chromium, desktop WebKit and Pixel 7 Chrome emulation: all font files/presets, landing/iframe synchronization, completed workouts, spacing extremes and reload persistence.
- Native: 205 JVM tests passed, lint reported zero errors, final debug APK installed with data preserved on the emulator, and bundled-font instrumentation passed (21 families/252 measurements). Native work remains separate and uncommitted in the existing Android workspace. No physical phone update or public APK release.
- Remaining limits: physical iPhone/Home Screen/VoiceOver verification is still outstanding. Native bottom-navigation labels have existing clipping at 320dp/200% system text. Geometry, platform-owned text and minimum touch targets deliberately do not scale with layout spacing.
