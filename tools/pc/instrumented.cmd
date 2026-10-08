@echo off
rem Instrumented suite on the PC emulator (never on a real phone).
set ADB=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe
%ADB% wait-for-device
%ADB% shell settings put global window_animation_scale 0
%ADB% shell settings put global transition_animation_scale 0
%ADB% shell settings put global animator_duration_scale 0
%ADB% shell settings put global hide_error_dialogs 1
%ADB% shell settings put system screen_off_timeout 2147483647
%ADB% shell input keyevent 82
%ADB% shell am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS >nul
cd /d C:\dev\PuzzleHelper && git pull -q && gradlew.bat connectedDebugAndroidTest --console=plain
