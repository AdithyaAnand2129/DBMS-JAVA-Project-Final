@echo off
setlocal
cd /d "%~dp0"

where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven is not installed or not on PATH.
    echo Install Maven and reopen PowerShell, then run this script again.
    pause
    exit /b 1
)

cls
echo ===============================
echo RoomWise Launcher
echo ===============================
echo 1. Start Swing GUI
echo 2. Start Terminal Console
echo 3. Exit
set /p choice="Choose an option: "

if "%choice%"=="1" (
    call mvn -q -DskipTests dependency:copy-dependencies package
    java -cp "%CD%\target\classes;%CD%\target\dependency\*" roomwise.Main
    exit /b %errorlevel%
)

if "%choice%"=="2" (
    call mvn -q -DskipTests dependency:copy-dependencies package
    java -cp "%CD%\target\classes;%CD%\target\dependency\*" roomwise.Main --console
    exit /b %errorlevel%
)

if "%choice%"=="3" (
    exit /b 0
)

echo Invalid option.
pause
