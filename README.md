# IronLog

Offline-first workout tracking for Android.

## Download

Get the latest APK from the [Releases](https://github.com/DeccanHYD/Ironlog/releases) page.

## Features

- Fast workout logging with set-by-set tracking, rest timer support, resume flow, and in-workout exercise actions
- Training plans with editable workout days, built-in program templates, onboarding-based setup, and plan recommendations
- Large exercise library with search, swap/add flows, custom exercise support, and YouTube demo links from workout menus
- Volume analytics with weekly totals, muscle-group breakdown, push/pull/legs balance, and radar-style volume visualization
- Recovery tracking with a front/back muscle heatmap, expanded body map view, and touch-based muscle inspection
- Progress tracking for body weight, body measurements, progress photos, workout history, PRs, and calendar views
- Local-first utilities including backup/restore flows, CSV export/import, privacy controls, gym profiles, image caching, and theme customization
- App feel upgrades such as semantic haptics, smoother gesture behavior, dark-first theming, and focused mobile-first UI

## What Stands Out

- Offline-first by default. IronLog is built around local data first instead of treating the phone like a thin client.
- Interactive muscle recovery map. The app includes a front/back body map with per-region recovery states instead of a generic readiness score.
- Real-history program intelligence. The home recommendation card uses recent training behavior, recovery, and volume patterns instead of fake chatbot copy.
- Shareable analytics. Volume insights are designed to be turned into shareable cards, not just hidden in static charts.
- Gym-lifter utilities most apps skip. Gym profiles, bar-weight setup, CSV import/export, and in-workout YouTube exercise access are built into the core app flow.

## Build From Source

```bash
npm install
npx expo run:android
```

If you need native folders generated locally first:

```bash
npx expo prebuild
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

Released under the [MIT License](LICENSE).
