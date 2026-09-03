@echo off
chcp 65001 >nul
cd /d "%~dp0.."
echo ==== DMS 今日变更枚举 + 打包 ====
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0_today_changes.ps1"
echo.
pause
