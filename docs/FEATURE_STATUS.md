# IronlogDB Feature Status Snapshot

Last updated: 2026-04-29

## Branding and release policy

- App name surface: `IronlogDB` (launcher + startup + onboarding).
- Legacy release policy applied in README:
  - `v1.0.0`, `v1.1.0-beta`, `v1.1.0` are Legacy Alpha and unsupported.

## Core training flow

- Active workout logging: implemented.
- Rest timer panel + controls: implemented.
- Warm-up generation: implemented and now reacts to active profile bar weight changes.
- Set row edit/delete/type/note/rpe/rir: implemented.

## Athlete profile integration

- Gym profiles (bar + plates): implemented.
- Unit-aware profile editing/list display (`kg/lbs`): implemented.
- Copy-previous prefill:
  - Unit-aware for weights
  - Tracking-type aware for duration-style exercises
  - Implemented via `COPY_PREVIOUS_CUSTOM`.

## Theming and UX stability

- PlatformColor/Monet unsafe string concat scan (`colors.* + 'xx'`) in active `src/` paths: clean.
- Custom alert theming: themed and PlatformColor-safe.
- Remaining manual QA still required across all themes for edge screens and modal/button contrast.

## Data and migration direction

- Watermelon migration work is in progress in this repo.
- Legacy and migration code paths coexist during transition.
- Final runtime-only Watermelon closure still requires full integration QA.

## Pending high-priority QA

1. Active workout smoothness on real device (long session).
2. Athlete profile -> warm-up/plate math verification in both `kg` and `lbs`.
3. Restore/import regression checks (legacy backups to current runtime).
4. Theme contrast sweep on Light/Dark/Amoled/Monet.

