@echo off
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-PhonChinese.ps1" %*
if errorlevel 1 pause
