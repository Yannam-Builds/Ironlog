# Contributing to IronLog

IronLog is proprietary software. It does not operate an open contribution program.

## Approval required

Do not submit code, patches, designs, icons, artwork, animations, exercise media, datasets, copy, or other assets unless both conditions are complete **before submission**:

1. the maintainer has approved the specific scope in writing; and
2. you and the maintainer have signed the required contribution and IP assignment agreement.

An issue, discussion, fork, security report, or pull-request form does not provide approval. Unsolicited submissions may be closed without review. Receipt of material does not grant rights to the IronLog source, name, branding, store identity, signing material, or any other project asset.

Bug reports and feature suggestions are welcome when they do not include implementation code or creative assets. Report vulnerabilities through [SECURITY.md](SECURITY.md).

## Authorized contributors

Keep approved changes within scope, document the user-visible outcome and risks, and never commit API keys, `local.properties`, keystores, fitness exports, databases, progress photos, device logs, or generated build output. Identify the source and license of every approved third-party dependency or asset.

Run focused tests while developing, then run the public verification gate:

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Use `gradlew.bat` on Windows. Release builds require local signing material and are not part of the public CI gate.

In an authorized pull request, include the written-approval reference, verification results, relevant screenshots or recordings, and risks involving persistence, migrations, lifecycle, notifications, camera, Health Connect, import/export, widgets, or signing. Device-dependent changes should be checked on real hardware when possible.
