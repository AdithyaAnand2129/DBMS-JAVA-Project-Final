@echo off
setlocal
cd /d "%~dp0"
where mvn >nul 2>nul
if errorlevel 1 (
    echo Install Maven and add it to PATH.
    pause
    exit /b 1
)
call mvn -q package dependency:copy-dependencies -DincludeScope=runtime
if errorlevel 1 (
    echo Build failed.
    pause
    exit /b 1
)
java -cp "target/classes;target/dependency/*" roomwise.Main
set "appExit=%errorlevel%"
call mvn -q clean
if errorlevel 1 echo Could not clean generated output. Close applications using target and run mvn clean.
exit /b %appExit%
