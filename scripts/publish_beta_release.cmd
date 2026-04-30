@echo off
setlocal

set GH_EXE=
if exist "%ProgramFiles%\GitHub CLI\gh.exe" set GH_EXE=%ProgramFiles%\GitHub CLI\gh.exe
if "%GH_EXE%"=="" if exist "%ProgramFiles(x86)%\GitHub CLI\gh.exe" set GH_EXE=%ProgramFiles(x86)%\GitHub CLI\gh.exe

if "%GH_EXE%"=="" (
  echo GH_NOT_FOUND
  exit /b 2
)

"%GH_EXE%" auth status >nul 2>&1
if errorlevel 1 (
  echo GH_AUTH_REQUIRED
  exit /b 3
)

set RELEASE_NOTES=.github\release-1.1.0-beta.md
if not exist ".github" mkdir ".github"
(
echo IRONLOG 1.1.0 Beta
echo.
echo - Full 2.0 closure pass foundations completed (notifications, migration safety, restore paths, analytics hardening^).
echo - Smart notification engine upgraded with policy profiles, caps, cooldowns, quiet-hour-safe scheduling, and decision logging.
echo - Program picker templates validated and corrected ^(30/30 pass^).
echo - Added first-launch restore flow for reinstall users ^(encrypted backup + SQLite import^).
echo - Added QA closure artifacts and verification docs.
echo.
echo APK:
echo - IRONLOG-RC-1.1.0-qa100.apk
) > "%RELEASE_NOTES%"

"%GH_EXE%" release view v1.1.0-beta >nul 2>&1
if errorlevel 1 (
  "%GH_EXE%" release create v1.1.0-beta "android/app/build/outputs/apk/release/IRONLOG-RC-1.1.0-qa100.apk#IRONLOG-RC-1.1.0-qa100.apk" --title "IRONLOG v1.1.0 Beta" --notes-file "%RELEASE_NOTES%" --prerelease
) else (
  "%GH_EXE%" release upload v1.1.0-beta "android/app/build/outputs/apk/release/IRONLOG-RC-1.1.0-qa100.apk#IRONLOG-RC-1.1.0-qa100.apk" --clobber
  "%GH_EXE%" release edit v1.1.0-beta --title "IRONLOG v1.1.0 Beta" --notes-file "%RELEASE_NOTES%" --prerelease
)

if errorlevel 1 (
  echo GH_RELEASE_FAILED
  exit /b 4
)

echo GH_RELEASE_DONE
exit /b 0

