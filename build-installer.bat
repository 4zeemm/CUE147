@echo off
REM Builds the Windows installer: target\installer\CUE 147-<version>.msi
REM Needs JDK 17 or newer (jpackage is part of the JDK) and WiX v3 (https://wixtoolset.org) on the PATH.
setlocal enabledelayedexpansion
cd /d "%~dp0"

for /f "tokens=2 delims=<>" %%v in ('findstr /n "<version>" pom.xml ^| findstr "^3:"') do set VERSION=%%v
if "%VERSION%"=="" set VERSION=2.0.0
echo Building CUE 147 %VERSION% ...

call mvnw.cmd -q -B -DskipTests package || exit /b 1

if exist target\installer-input rmdir /s /q target\installer-input
if exist target\installer rmdir /s /q target\installer
mkdir target\installer-input
mkdir target\installer
copy /y "target\cue147-%VERSION%.jar" target\installer-input\ >nul
xcopy /e /i /y target\lib target\installer-input\lib >nul

jpackage ^
  --name "CUE 147" ^
  --app-version "%VERSION%" ^
  --input target\installer-input ^
  --main-jar "cue147-%VERSION%.jar" ^
  --main-class app.Main ^
  --icon packaging\cue147.ico ^
  --type msi ^
  --dest target\installer ^
  --vendor "CUE 147" ^
  --copyright "CUE 147" ^
  --description "Snooker and pool club management" ^
  --win-shortcut ^
  --win-menu ^
  --win-menu-group "CUE 147" ^
  --win-dir-chooser ^
  --win-per-user-install ^
  --java-options "-Xmx512m" ^
  --java-options "--enable-native-access=ALL-UNNAMED" || exit /b 1

echo.
echo Done:
dir target\installer
echo.
echo Double-click the .msi to install. CUE 147 then appears in the Start menu and on the desktop.
