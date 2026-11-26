@echo off
title AquaSentinel Desktop Application
cd /d "%~dp0"
echo Starting AquaSentinel...
call .\mvnw.cmd javafx:run
pause
