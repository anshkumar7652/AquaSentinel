@echo off
setlocal enabledelayedexpansion
title AquaSentinel - Standalone Portable Packager
cd /d "%~dp0"

echo ==============================================================================
echo                 AquaSentinel Standalone Packager (Zero-Install)
echo ==============================================================================
echo.

:: 1. Verify Java/jpackage availability
where jpackage >nul 2>nul
if %ERRORLEVEL% neq 0 (
    echo [ERROR] 'jpackage' tool not found in PATH!
    echo Please ensure Java 21+ JDK is installed and configured in your PATH.
    pause
    exit /b 1
)

:: 2. Clean and Build Shaded Fat JAR
echo [1/4] Compiling project and assembling standalone JAR...
call .\mvnw.cmd clean package -DskipTests
if %ERRORLEVEL% neq 0 (
    echo [ERROR] Maven build failed.
    pause
    exit /b 1
)

:: 3. Prepare JPackage input staging directory
echo [2/4] Staging deployment binaries...
if exist target\jpackage-input rmdir /s /q target\jpackage-input
mkdir target\jpackage-input

copy target\aquasentinel-*-standalone.jar target\jpackage-input\AquaSentinel.jar >nul
if not exist target\jpackage-input\AquaSentinel.jar (
    echo [ERROR] Standalone JAR was not produced in target directory.
    pause
    exit /b 1
)

:: 4. Build Standalone Native Windows App Bundle via jpackage
echo [3/4] Creating standalone Windows application image via jpackage...
if exist dist\AquaSentinel rmdir /s /q dist\AquaSentinel

jpackage ^
  --type app-image ^
  --dest dist ^
  --input target/jpackage-input ^
  --name AquaSentinel ^
  --main-jar AquaSentinel.jar ^
  --main-class com.aquasentinel.app.AppLauncher ^
  --app-version 1.0.0 ^
  --vendor "AquaSentinel"

if %ERRORLEVEL% neq 0 (
    echo [ERROR] jpackage failed to generate native Windows image.
    pause
    exit /b 1
)

:: 5. Create ready-to-share ZIP bundle
echo [4/4] Compressing portable ZIP package for distribution...
powershell -NoProfile -Command "Compress-Archive -Path 'dist\AquaSentinel' -DestinationPath 'dist\AquaSentinel-v1.0.0-Portable-Windows.zip' -Force"

echo.
echo ==============================================================================
echo [SUCCESS] Standalone portable package created successfully!
echo.
echo Outputs generated:
echo   1. Unpacked Folder : dist\AquaSentinel\ (Run: dist\AquaSentinel\AquaSentinel.exe)
echo   2. Shareable ZIP   : dist\AquaSentinel-v1.0.0-Portable-Windows.zip
echo.
echo Non-technical users only need to extract the ZIP and double-click AquaSentinel.exe!
echo (No Java or any software installation required on their computer).
echo ==============================================================================
echo.
pause
