@echo off
setlocal

set TAG=%1
if "%TAG%"=="" set TAG=v1.1.0

set TITLE=IRONLOG %TAG%
set SOURCE_APK=android/app/build/outputs/apk/release/app-release.apk
set OUT_DIR=release_builds
set APK_NAME=IRONLOG-%TAG%-android.apk
set APK_PATH=%OUT_DIR%\\%APK_NAME%
set NOTES_FILE=.github/release-%TAG%.md

if not exist "%SOURCE_APK%" (
  echo APK_NOT_FOUND: %SOURCE_APK%
  exit /b 1
)

if not exist "%OUT_DIR%" mkdir "%OUT_DIR%"
copy /Y "%SOURCE_APK%" "%APK_PATH%" >nul

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

if not exist ".github" mkdir ".github"
(
echo %TITLE%
echo.
echo - Bare React Native Android stabilized build.
echo - Local-first SQLite with migration continuity hardening.
echo - Backup Center, encrypted backup/restore, and SQLite import/export improvements.
echo - Navigation/header visual consistency and analytics card alignment fixes.
echo.
echo APK artifact:
echo - %APK_NAME%
) > "%NOTES_FILE%"

"%GH_EXE%" release view %TAG% >nul 2>&1
if errorlevel 1 (
  "%GH_EXE%" release create %TAG% "%APK_PATH%#%APK_NAME%" --title "%TITLE%" --notes-file "%NOTES_FILE%"
) else (
  "%GH_EXE%" release upload %TAG% "%APK_PATH%#%APK_NAME%" --clobber
  "%GH_EXE%" release edit %TAG% --title "%TITLE%" --notes-file "%NOTES_FILE%"
)

if errorlevel 1 (
  echo GH_RELEASE_FAILED
  exit /b 4
)

echo GH_RELEASE_DONE
exit /b 0
