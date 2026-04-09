# Release Commands (Windows)

## Build APK
```bat
cd /d Z:\ironlog\android
gradlew.bat assembleRelease --console=plain
```

## Verify APK
```bat
dir Z:\ironlog\android\app\build\outputs\apk\release
```

## Install on Device
```bat
adb install -r Z:\ironlog\android\app\build\outputs\apk\release\app-release.apk
```

## Git Push + Tag
```bat
cd /d Z:\ironlog
git add .
git commit -m "chore: v1.0.0 release"
git push origin main
git tag -a v1.0.0 -m "IronLog v1.0.0"
git push origin v1.0.0
```

