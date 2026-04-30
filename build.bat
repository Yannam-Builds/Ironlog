@echo off
cd /d Z:\ironlog\android
call gradlew.bat assembleRelease > Z:\ironlog\build_round2_audit.txt 2>&1
