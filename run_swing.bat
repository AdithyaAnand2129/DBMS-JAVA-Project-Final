@echo off
setlocal
cd /d "%~dp0"

where mvn >nul 2>nul
if errorlevel 1 (
    echo Maven is not installed or not on PATH.
    echo Install Maven and reopen PowerShell, then try again.
    pause
    exit /b 1
)

call mvn -q -DskipTests dependency:copy-dependencies package
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)

java -cp "%CD%\target\classes;%CD%\target\dependency\*" roomwise.Main

