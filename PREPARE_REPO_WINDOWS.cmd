@echo off
setlocal
cd /d "%~dp0"
echo.
echo Civic FA1 Dashboard v1.1.0 - prepare repository
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0tools\clean_legacy_windows.ps1"
set RC=%ERRORLEVEL%
echo.
if not "%RC%"=="0" (
  echo PREPARE FAILED. Nothing was committed automatically.
) else (
  echo PREPARE PASS. Open GitHub Desktop and verify the changes before Commit/Push.
)
echo.
pause
exit /b %RC%
