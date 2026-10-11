@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0.."

git status --porcelain | findstr . >nul
if not errorlevel 1 (
    echo 还有没提交的改动，先提交再导出：
    git status --short
    pause
    exit /b 1
)

for /f %%i in ('powershell -NoProfile -Command "Get-Date -Format yyyyMMdd-HHmm"') do set TS=%%i
set OUT=..\gtlmodular-%TS%.bundle
git bundle create "%OUT%" --all
if errorlevel 1 (
    echo 导出失败。
    pause
    exit /b 1
)
echo.
echo 已导出：%OUT%
echo 把这个文件发给对方即可。
pause
