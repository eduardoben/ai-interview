@echo off
set "EMULATOR_EXE=%LOCALAPPDATA%\Android\Sdk\emulator\emulator.exe"

if not exist "%EMULATOR_EXE%" (
    echo [ERROR] Android Emulator not found at: "%EMULATOR_EXE%"
    pause
    exit /b 1
)

echo ================================================================
echo  Starting Android Multi-Device Fleet (4 Emulators Concurrently)
echo ================================================================
echo.
cd /d "%LOCALAPPDATA%\Android\Sdk\emulator"

echo [1/4] Starting Pixel 10 Pro XL (Android 17, Flagship)...
start "" "%EMULATOR_EXE%" -avd Pixel_10_Pro_XL
ping 127.0.0.1 -n 5 >nul

echo [2/4] Starting Samsung Galaxy S25 Ultra (Android 17, Flagship)...
start "" "%EMULATOR_EXE%" -avd Galaxy_S25_Ultra
ping 127.0.0.1 -n 5 >nul

echo [3/4] Starting Samsung Galaxy Tab S7+ (Android 17, Tablet)...
start "" "%EMULATOR_EXE%" -avd Galaxy_Tab_S7_Plus
ping 127.0.0.1 -n 5 >nul

echo [4/4] Starting Samsung Galaxy A54 5G (Android 13, Mid-Tier)...
start "" "%EMULATOR_EXE%" -avd Galaxy_A54_5G

echo.
echo All 4 emulators dispatched successfully!
ping 127.0.0.1 -n 3 >nul
exit /b 0
