@echo off
cd /d Z:\ironlog\android
call gradlew.bat assembleRelease
if %errorlevel% neq 0 (
  echo BUILD FAILED
  exit /b 1
)
echo BUILD SUCCESS - Installing...
adb install -r Z:\ironlog\android\app\build\outputs\apk\release\app-release.apk
