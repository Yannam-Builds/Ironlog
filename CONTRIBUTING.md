# Contributing to IronLog

Thanks for helping improve IronLog. Changes should preserve three properties: workout logging remains fast, the primary record remains trustworthy, and optional integrations do not become requirements for basic use.

## Before opening a change

1. Search existing issues and pull requests.
2. Keep the change focused and explain the user-visible outcome.
3. Do not commit API keys, `local.properties`, keystores, fitness exports, progress photos, or generated build output.
4. Add or update tests for behavioral changes.

## Local checks

Run the relevant focused tests while developing, then run the full gate before opening a pull request:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Use `gradlew.bat` on Windows. Release builds require your own local signing material and are not part of the public CI gate.

## Pull requests

Describe the problem, the chosen behavior, risk areas, and how you verified it. Include screenshots or a short recording for visual changes. Body-map, camera, widget, notification, Health Connect, and lifecycle changes should be checked on a real device when possible.

The repository uses a personal-use source license. Opening a contribution does not change the project license or grant rights to the IronLog name, store identity, or signing keys.
