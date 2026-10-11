@echo off
chcp 65001 >nul
setlocal
cd /d "%~dp0.."

if "%~1"=="" (
    echo 用法：把收到的 .bundle 文件拖到这个脚本上。
    pause
    exit /b 1
)
git pull "%~1" main
pause
