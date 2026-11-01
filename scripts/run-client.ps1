# 启动开发客户端（runClient）——在本机桌面弹出游戏窗口，用于验证机器
# 用法: .\scripts\run-client.ps1
$ErrorActionPreference = 'Stop'
$env:JAVA_HOME = 'D:\Users\DELL\AppData\Roaming\red.ghs.axolotl\meta\java_versions\mojang-java-runtime-gamma-windows-x64-89ce85ccb518'
$env:GRADLE_USER_HOME = 'E:\大鲸鱼工作区\gtl-dev\gradle-home'
$proj = Split-Path -Parent $PSScriptRoot
Push-Location $proj
try {
  & .\gradlew.bat runClient "-Porg.gradle.java.installations.paths=$env:JAVA_HOME"
  if ($LASTEXITCODE -ne 0) { Write-Output ('runClient exit=' + $LASTEXITCODE); exit $LASTEXITCODE }
  Write-Output 'runClient exited'
} finally { Pop-Location }
