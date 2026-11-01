# 从本机 GTL 实例 mods 目录复制开发依赖 jar 到项目 libs/
# 用法: .\scripts\copy-libs.ps1
$ErrorActionPreference = 'Stop'

# TODO: 改成你自己的 GTL 实例路径（或首次运行时按提示输入）
$src = 'D:\Users\DELL\AppData\Roaming\red.ghs.axolotl\profiles\GTL推荐新手玩版（较难）_versions_GTL\mods'
if (-not (Test-Path -LiteralPath $src)) {
    $src = Read-Host '未找到 GTL 实例 mods 目录，请输入完整路径'
}

$dst = Join-Path $PSScriptRoot '..\libs'
New-Item -ItemType Directory -Force -Path $dst | Out-Null

# 需要引入 classpath 的 jar（编译/运行均需要；GTL 扩展 jar 目前走弱引用可不放，但放开便于反编译核对）
$names = @(
    'gtceu-1.20.1-1.4.4.jar',                          # GTM（必）
    'architectury-9.2.14-forge.jar',                   # LDLib 依赖（必）
    'ldlib-forge-1.20.1-1.0.33.b.jar',                 # LDLib 运行版（编译也可用）
    'gtlcore-1.2.3.2-fix2.jar',                        # GTL Core（选：蓝本/反编译）
    'gtladditions-3.2.8Custom_SubSpace-fix2.jar'       # gtladditions（选：配方来源/反编译；Java 21 字节码 ⇒ 编译需 JDK 21）
)

$ok = 0; $miss = 0
foreach ($n in $names) {
    $f = Join-Path $src $n
    if (Test-Path -LiteralPath $f) {
        Copy-Item -LiteralPath $f -Destination (Join-Path $dst $n) -Force
        Write-Output ("OK   " + $n); $ok++
    } else {
        Write-Output ("MISS " + $n); $miss++
    }
}
Write-Output ("done: copied=$ok missing=$miss  ->  " + (Resolve-Path $dst))
