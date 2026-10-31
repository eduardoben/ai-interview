@echo off
setlocal enabledelayedexpansion
title Android Multi-Device Emulator Hub

set "EMULATOR_EXE=%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe"
set "ADB_EXE=%LOCALAPPDATA%\Android\Sdk\platform-tools\adb.exe"

if not exist "%EMULATOR_EXE%" (
    echo [ERROR] Android Emulator not found at: "%EMULATOR_EXE%"
    pause
    exit /b 1
)

:menu
cls
echo ================================================================
echo          ANDROID MULTI-DEVICE EMULATOR LAUNCHER
echo ================================================================
echo.
echo   [1] Launch ALL 4 Emulators Concurrently
echo       (Pixel 10 Pro XL, Galaxy S25 Ultra, Tab S7+, Galaxy A54)
echo.
echo   [2] Launch Pixel 10 Pro XL        (Android 17, 1344x2992, Flagship)
echo   [3] Launch Samsung Galaxy S25 Ultra (Android 17, 1440x3120, Flagship)
echo   [4] Launch Samsung Galaxy Tab S7+   (Android 17, 2800x1752, Tablet)
echo   [5] Launch Samsung Galaxy A54 5G    (Android 13, 1080x2340, Mid-Tier)
echo.
echo   [6] View Running Emulator Status  (adb devices -l)
echo   [7] Stop / Kill ALL Running Emulators
echo.
echo   [0] Exit
echo ================================================================
set /p choice="Enter your selection [0-7]: "

if "%choice%"=="1" goto launch_all
if "%choice%"=="2" goto launch_pixel
if "%choice%"=="3" goto launch_s25
if "%choice%"=="4" goto launch_tabs7
if "%choice%"=="5" goto launch_a54
if "%choice%"=="6" goto show_status
if "%choice%"=="7" goto kill_all
if "%choice%"=="0" exit /b 0

echo Invalid selection.
ping 127.0.0.1 -n 3 >nul
goto menu

:launch_pixel
echo.
echo Launching Pixel 10 Pro XL...
cd /d "%LOCALAPPDATA%\Android\Sdk\emulator"
start "" "%EMULATOR_EXE%" -avd Pixel_10_Pro_XL
echo Done.
ping 127.0.0.1 -n 3 >nul
goto menu

:launch_s25
echo.
echo Launching Galaxy S25 Ultra...
cd /d "%LOCALAPPDATA%\Android\Sdk\emulator"
start "" "%EMULATOR_EXE%" -avd Galaxy_S25_Ultra
echo Done.
ping 127.0.0.1 -n 3 >nul
goto menu

:launch_tabs7
echo.
echo Launching Galaxy Tab S7+...
cd /d "%LOCALAPPDATA%\Android\Sdk\emulator"
start "" "%EMULATOR_EXE%" -avd Galaxy_Tab_S7_Plus
echo Done.
ping 127.0.0.1 -n 3 >nul
goto menu

:launch_a54
echo.
echo Launching Galaxy A54 5G (Android 13)...
cd /d "%LOCALAPPDATA%\Android\Sdk\emulator"
start "" "%EMULATOR_EXE%" -avd Galaxy_A54_5G
echo Done.
ping 127.0.0.1 -n 3 >nul
goto menu

:launch_all
echo.
echo ================================================================
echo Launching all 4 emulators with staggered startup...
echo ================================================================
cd /d "%LOCALAPPDATA%\Android\Sdk\emulator"

echo [1/4] Starting Pixel 10 Pro XL...
start "" "%EMULATOR_EXE%" -avd Pixel_10_Pro_XL
ping 127.0.0.1 -n 5 >nul

echo [2/4] Starting Galaxy S25 Ultra...
start "" "%EMULATOR_EXE%" -avd Galaxy_S25_Ultra
ping 127.0.0.1 -n 5 >nul

echo [3/4] Starting Galaxy Tab S7+...
start "" "%EMULATOR_EXE%" -avd Galaxy_Tab_S7_Plus
ping 127.0.0.1 -n 5 >nul

echo [4/4] Starting Galaxy A54 5G (Android 13)...
start "" "%EMULATOR_EXE%" -avd Galaxy_A54_5G
echo.
echo All 4 emulators dispatched!
ping 127.0.0.1 -n 4 >nul
goto menu

:show_status
echo.
echo Current ADB Device List:
echo ----------------------------------------------------------------
if exist "%ADB_EXE%" (
    "%ADB_EXE%" devices -l
) else (
    adb devices -l
)
echo ----------------------------------------------------------------
echo Press any key to return to menu...
pause >nul
goto menu

:kill_all
echo.
echo Stopping all emulator processes...
taskkill /F /IM qemu-system-x86_64.exe >nul 2>&1
taskkill /F /IM emulator.exe >nul 2>&1
echo Emulators stopped.
ping 127.0.0.1 -n 3 >nul
goto menu
