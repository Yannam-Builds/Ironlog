# IronLog Web

Local-first React/TypeScript application and product landing page for GitHub Pages. This is a separate web project; it does not build or modify Android.

## Run

Use Node 24 and the checked-in lockfile:

```sh
npm ci
npm run dev
```

Landing: `http://127.0.0.1:5173/Ironlog/`. App: `/Ironlog/app/#/home`.

The landing CTA launches the working app inside a phone-sized iframe. It is a browser preview, not an Android emulator or a desktop dashboard. **Open app full-screen** uses the same origin and saved data; use that entry for Android Chrome/iPhone Safari and Home Screen installation. The iframe loads only after a user launches it, and theme selection applies to both experiences.

For production/offline verification:

```sh
npm test
npm run build
npm run verify:output
npx playwright install chromium webkit
npm run preview -- --port 4173
# Separate terminal:
npm run test:e2e
```

Service workers run in the production preview, not the development server. Browser tests use port 4173 by default; `PLAYWRIGHT_BASE_URL` can override it. Do not rebuild while a browser acceptance run is in progress.

## Architecture

- `src/data/`: validated Dexie transactions, serial workout mutations, revision conflicts, local snapshot subscription.
- `src/domain/`: calculations, date rules, plan/backup codecs and manual external-AI prompt construction.
- `src/features/`: onboarding, the five native tabs, detail screens and editor flows.
- `src/ui/`: native theme tokens, opaque dialogs, shared controls, body-map rendering and update prompt.
- `src/landing.tsx`: landing page, interactive phone preview and a separately labeled synthetic walkthrough.
- `src/research.tsx`: shared, six-paper registry with feature relevance and limitations.
- `tests/`: synthetic regression fixtures, desktop Chromium/WebKit journeys and Pixel 7 Chrome emulation for the phone preview.

See [domain fidelity](docs/domain-parity.md) and [acceptance status](docs/acceptance.md). Do not infer full native parity or physical-iPhone verification from a passing unit test suite.

## Native identity and provenance

Website work starts from public main `19947d7cad8a46fc9a74904ea6a85ef54f42ac93`. The newer native working tree was a read-only reference. `src/generated/native-provenance.json` records its revision and hashes of each selected source file; file hashes identify dirty native content precisely.

`scripts/extract-native.mjs --source <native-repository>` extracts all 12 palettes and 24 roles, 32 templates, the 1,731-exercise library, the body-map dataset, Lexend, and selected public artwork. Never point this extractor at a backup or personal-data directory.

The current monochrome logo comes directly from the native `res/drawable/ic_launcher_foreground.xml` path. `public/assets/ironlog-logo.svg` has a transparent background and is used inline and as the favicon. Inline marks are white on dark themes and inverted to black in Light. Only the generated Home Screen PNG icons retain an opaque black platform-icon background. The retired orange launcher image is not shipped. `build-assets.mjs` makes mobile-sized WebP copies of native illustrations without redesigning them.

Native tokens remain intact. The landing page uses lighter Lexend weights (350 hero, 450 section headings, 500 brand); app headings retain the native heavier treatment. Documented web accessibility adaptations include high-contrast button ink, visible input borders, bounded keyboard-accessible numeric entry, adaptive compact controls and opaque browser dialogs with forward/backward focus wrapping. Monet uses the native fallback, not wallpaper colors.

## Storage and portability

### Fonts and layout spacing

Use **Settings → Typography** for font family and weight, and **Settings → Layout spacing** for the compact-to-spacious slider. The landing page has the same font picker. Original preserves existing screen weights; Light uses body/control/heading weights 350/450/450, Regular 400/500/600, and Bold 500/650/750. Weights are clamped to each variable font's supported range. **Lexend + Light** gives the lighter landing-page feel throughout the app.

The 21 families are Lexend, Inter, Manrope, DM Sans, Plus Jakarta Sans, Outfit, Sora, Urbanist, Nunito, Nunito Sans, Rubik, Work Sans, Public Sans, Figtree, Assistant, Mulish, Quicksand, Raleway, Montserrat, Exo 2 and Source Sans 3. Original TTF files total **5.84 MiB**. All have SIL Open Font License notices linked from the picker and bundled for offline reading. The app precaches all fonts after startup so changing to an unused family also works offline; the landing picker itself does not preload every family.

`src/generated/fonts.json` records ranges, source URLs, SHA-256 hashes and the pinned Google Fonts revision `ade3d1533e06b2b1462ffcde8e08b129627ca360`. `scripts/fetch-fonts.mjs` is an explicit maintenance command, not a network build step. `scripts/font-faces.mjs` regenerates CSS locally during builds. No runtime font provider receives requests.

Spacing ranges from 85% to 125% in 5% steps. It changes positive CSS padding, margins and layout gaps, not text size, icons, chart/body-map geometry or minimum control sizes. Safe-area insets stay unscaled. Reset spacing restores 100%. Browser/OS-controlled menus and controls may retain platform styling.

Typography and spacing use separate validated localStorage preferences, synchronized across same-origin tabs and the embedded preview. They are **device preferences, not workout records**: browser backups and Android-compatible exports do not carry them, and they do not sync to the Kotlin app. Failed preference writes leave the previous appearance intact and show an error. Clearing site storage resets them. The separately implemented native controls are not shipped by the website deployment.

IndexedDB is the source of truth. There is no backend, account, analytics, automatic external-AI request or phone synchronization. Version 2 adds a single-row bundled catalog while preserving version-1 custom exercise overrides.

Complete browser ZIP backups contain photo bytes. Android-compatible JSON is a separate export; Android photo URIs do not transfer image files. Restore is validated before clearing and committed atomically. Restoring is blocked transactionally while any workout is active, including a session started in another tab after preview.

Browser storage is not a guaranteed backup. Safari and installed Home Screen contexts may have separate stores. Export before switching, clearing site data, or testing storage recovery. Never test reset against a real user's browser profile.

Warmups remain pending until explicitly logged. Rest timers persist deadlines rather than relying on background intervals. Screen wake lock is optional. Locked-screen alarms, native widgets, Health Connect and Android on-device AI are not available here.

## Updates and deployment

The PWA uses prompt updates. Pending writes and active workouts disable update controls. Worker activation, including activation from another tab, never automatically reloads the page. A user explicitly reloads when ready. Public artwork receives content revisions; overlapping precache entries are prohibited.

`../.github/workflows/web-pages.yml` tests/builds only `web/` and uploads only `web/dist`. It retains existing Android CI. Hash routing avoids GitHub Pages rewrite rules. Keep base `/Ironlog/`; follow the [Vite GitHub Pages instructions](https://vite.dev/guide/static-deploy.html).

**Preview publication requested by the owner on 31 August 2026.** Repository publication is separate from full product acceptance. The workflow must pass before Pages deployment; never skip a failed test to publish. Real iPhone testing and the documented parity/acceptance gaps remain open. Do not include unrelated Android edits, APKs, keystores or user backups.

On Windows, the appearance update passed 94 unit tests and all 49 main browser tests (2 workers, 2.1 minutes); CI retains its single-worker configuration. All 21 fonts and presets are checked on the landing/embedded app at 320px and 200% text in Chromium/WebKit/Pixel 7 Chrome emulation. Settings changes, completed workouts, failed font/preference loads, both spacing extremes, unchanged text/icon dimensions and control minimums are covered. Both supplemental origin-unavailable workout tests pass (2/2, 23.1 seconds). The main offline test closes its actual HTTP server; the standalone WebKit offline-emulation failure remains documented and reproducible. See [the diagnostic evidence and limitations](diagnostics/OFFLINE-WEBKIT.md); this is not physical-device or installed-PWA verification.

OpenGym exercise pictures/GIFs are not included. Its [media notice](https://gitlab.com/DuarteSantos8/opengym/-/blob/main/NOTICE.md) and the [upstream dataset terms](https://github.com/hasaneyldrm/exercises-dataset#license--usage) require separate media permission; software licenses do not grant those image rights.

See the [six-feature OpenGym reference](docs/opengym-feature-reference-2026-08-31.md) for independently written Kotlin requirements and source-level correctness findings. No competitor implementation or media was copied, and no native feature is claimed as shipped by this website pass.

## Skill provenance

Installed with the skill-installer helper without replacing existing directories:

- Anthropic Frontend Design: `3b3fad96af16a10759d930941b4520ba0c40edae`.
- Vercel React Best Practices and Composition Patterns: `063bee94c3f4df8453406c830b0a7df0f2860278`.

Existing Impeccable/redesign, accessibility, citation-verification, TDD, browser-testing and independent-review workflows guided implementation. Native IronLog identity takes precedence over generic styling advice.

## License

IronLog Personal Use License 1.0 applies; see the repository's `LICENSE`. Third-party software/font notices are distinct from research citations. Research informs design principles and does not validate IronLog's exact readiness formula.
