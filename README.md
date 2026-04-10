<div align="center">
  <img src="assets/github-logo.png" width="140" alt="IronLog logo" />

  <h1>IronLog</h1>

  <p><strong>Offline-first Android workout tracker with recovery heatmaps and practical training intelligence.</strong></p>

  <p>Built for lifters who want fast logging, clear muscle and recovery analytics, and local-first control over training data.</p>

  <p>
    <a href="https://github.com/Yannam-Builds/Ironlog/releases"><img src="https://img.shields.io/github/v/release/Yannam-Builds/Ironlog?label=stable" alt="Latest release" /></a>
    <a href="https://github.com/Yannam-Builds/Ironlog/releases"><img src="https://img.shields.io/github/downloads/Yannam-Builds/Ironlog/total" alt="Downloads" /></a>
    <a href="https://github.com/Yannam-Builds/Ironlog/commits"><img src="https://img.shields.io/github/last-commit/Yannam-Builds/Ironlog" alt="Last commit" /></a>
    <a href="LICENSE"><img src="https://img.shields.io/badge/license-source--available-orange.svg" alt="Source-available license" /></a>
  </p>

  <p>
    <a href="https://github.com/Yannam-Builds/Ironlog/releases/latest"><img src="https://img.shields.io/badge/Download-Latest_APK-ff6a00?style=for-the-badge" alt="Download latest APK" /></a>
    <a href="https://github.com/Yannam-Builds/Ironlog/releases/latest"><img src="https://img.shields.io/badge/Android-Only-1b1b1b?style=for-the-badge&logo=android&logoColor=3DDC84" alt="Android only" /></a>
  </p>

  <p><sub>Android 7.0+ | offline-first | backup and import/export built in</sub></p>
</div>

## Featured Screens

<table>
  <tr>
    <td align="center" width="50%">
      <img src="features/12-home-train-core-next-overview.jpg" alt="IronLog home screen with smart recommendation cards, weekly plan chips, and recovery heatmap." width="100%" />
      <br />
      <strong>Smart home overview</strong>
      <br />
      Program intelligence, weekly structure, bodyweight context, and recovery visibility in one screen.
    </td>
    <td align="center" width="50%">
      <img src="features/05-workout-logging-set-history.jpg" alt="IronLog workout logging screen with set history, target reps, and quick logging controls." width="100%" />
      <br />
      <strong>Workout logging</strong>
      <br />
      Fast set-by-set logging with history, notes, rest timers, smart targets, and in-workout actions.
    </td>
  </tr>
  <tr>
    <td align="center" width="50%">
      <img src="features/27-volume-analytics-week-summary-radar.jpg" alt="IronLog analytics screen showing weekly volume summary and muscle radar chart." width="100%" />
      <br />
      <strong>Volume analytics</strong>
      <br />
      Weekly summary cards, radar views, imbalance insights, and muscle breakdowns from real training data.
    </td>
    <td align="center" width="50%">
      <img src="features/13-muscle-recovery-front-inspector.jpg" alt="IronLog muscle recovery screen with front muscle map and tooltip inspection." width="100%" />
      <br />
      <strong>Recovery heatmaps</strong>
      <br />
      Interactive front and back muscle maps for freshness, fatigue, and workload inspection.
    </td>
  </tr>
</table>

See the full 30-image gallery in [features/README.md](features/README.md).

## Download

Download the latest Android APK from [GitHub Releases](https://github.com/Yannam-Builds/Ironlog/releases/latest).

## Release Status (Truth Map)

### 1.0 Baseline (Shipped)

- Fast workout logging with rest timers and set history.
- Plans/programs, exercise library, and custom exercise support.
- Volume analytics, recovery heatmap, and bodyweight analytics.
- Program recommendations and YouTube exercise demo links.

### 1.1.0-beta Additions (Shipped in beta)

- Program Insights, adaptive targets, and goal mode (`Hypertrophy`, `Strength`, `General Fitness`).
- Exercise progress dashboards (`E1RM`, `Load`, `Reps`, `Volume`, `Consistency`, `History`).
- Recovery score plus manual recovery check-ins (soreness, sleep, energy).
- Streaks, milestones, and weekly summary card surfacing.
- Smart notification policy controls: profile modes, quiet hours, cooldowns, snooze, and decision logging.
- Restore wizard, versioned SQLite export/import foundation, and backup parity hardening.
- Added set edit/delete controls and add-exercise flow during active workouts.

### What Is Actually Next (Pending)

- OpenWeight interoperability layer (import/export) and stronger migration paths from Strong and Hevy.
- Import Center with dry-run preview, alias review, duplicate detection, and import reports.
- Full parity closure for all 1.1.0-beta app-state domains in versioned export/import contracts.
- Coach-grade program builder (block/mesocycle logic, progression models, %1RM and optional RPE/RIR prescriptions).
- Adherence v2 (busy-week compression, fatigue-aware reshuffling, stronger rationale copy).
- Analytics explainability and confidence layering across Home, Recovery, and Volume insights.
- Performance, reliability, and accessibility hardening for large-history users.

## Data Ownership and Portability

- Internal source of truth: local SQLite on Android.
- Backup model today: local encrypted snapshots, SQLite export/import, and optional Google Drive backup targets.
- Google Drive supports AppData mode and visible folder mode; this is backup, not live sync.
- OpenWeight support is planned as an interoperability contract, not as the internal database architecture.

## Android Security and Play Protect Notes

To reduce Play Protect false positives and improve release trust:

- blocked permissions are set in `app.json` for risky capabilities not used by the app.
- local native manifest removes `RECORD_AUDIO` and `SYSTEM_ALERT_WINDOW`.
- release signing is configured for a real keystore and must not use debug signing.

If a device still warns after upgrading:

1. uninstall old debug-signed builds,
2. install the new release-signed build,
3. then submit a false-positive appeal if warning reputation lags.

## Why IronLog

IronLog is built to stay fast during training and useful after training. It keeps serious analytics practical, not noisy, while preserving local-first ownership of your data.

## Suggestions and Community Chat

- Feature requests and bug reports: [Issues](https://github.com/Yannam-Builds/Ironlog/issues/new/choose)
- Optional discussion threads: [GitHub Discussions](https://github.com/Yannam-Builds/Ironlog/discussions)

## Build From Source

```bash
npm install
npx expo run:android
```

If native folders need to be generated locally first:

```bash
npx expo prebuild
```

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) and [CODE_OF_CONDUCT.md](CODE_OF_CONDUCT.md).

## License

IronLog is source-available, not OSI open source.

It is released under the [IronLog Personal Use License](LICENSE) for personal and non-commercial use. Commercial use requires permission.
