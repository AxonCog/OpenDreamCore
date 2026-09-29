# OpenDreamCore 全版本同步门禁
# 用法: pwsh .github/scripts/sync_check.ps1
# 干什么: 逐 target 断言「契约实现」齐不齐——少了任何一块直接非零退出。
#          发布仓的 .github/workflows/ci.yml 前置调用它，杜绝"只给几个版本加了实现"。

$ErrorActionPreference = 'Stop'
$root = Split-Path (Split-Path $PSScriptRoot -Parent) -Parent   # 脚本在 .github/scripts/ 下，仓库根在两层之上
$modernRoot = Join-Path $root 'OpenDreamCore\targets'
$legacyRoot  = Join-Path $root 'OpenDreamCore-Legacy\targets'
# 共享生成源树：1.21.4+ 目标的字体/世界渲染 mixin 由它们提供（构建时复制进 build/generated/<gen>），
# 因此门禁扫源文件时必须把它们一起算进来。
$genDirs = @('glyph-1.20-1.21.4', 'glyph-1.21.5+', 'worldrender-1.21.8', 'worldrender-1.21.5+',
             'worldrender-pipe-1.21.11', 'worldrender-pipe-26.1.2')
$genRoot = Join-Path $root 'OpenDreamCore\gensrc'
$fail = 0

$modernTargets = @(
  'fabric-1.20.1','fabric-1.21.1','fabric-1.21.4','fabric-1.21.8','fabric-1.21.11','fabric-26.1.2',
  'forge-1.20.1',
  'neoforge-1.21.1','neoforge-1.21.4','neoforge-1.21.8','neoforge-1.21.11','neoforge-26.1.2'
)
$legacyTargets = @('forge-1.16.5','forge-1.12.2','forge-1.7.10','forge-1.6.4')

function Has-File($dir, $name) {
    Get-ChildItem $dir -Recurse -Filter $name -File -ErrorAction SilentlyContinue | Select-Object -First 1
}

function Check($label, $cond) {
    if ($cond) { Write-Output "  [OK] $label" } else { Write-Output "  [FAIL] $label"; $script:fail++ }
}

Write-Output '== 现代线: 桥总口契约 =='
foreach ($t in $modernTargets) {
    $src = Join-Path $modernRoot "$t\src\main\java"
    if (-not (Test-Path $src)) { Check "$t 源码目录" $false; continue }
    $bridges = Has-File $src '*Bridges.java'
    Check "$t XxxBridges 存在" ($null -ne $bridges)
    $bm = Get-Content $bridges.FullName -Raw -ErrorAction SilentlyContinue
    Check "$t TargetBridges 实现" ($bm -match 'implements\s+TargetBridges')
    Check "$t entityBridge()" ($bm -match 'entityBridge\(')
    Check "$t itemModelBridge()" ($bm -match 'itemModelBridge\(')
    Check "$t packInjector()" ($bm -match 'packInjector\(')
    Check "$t EntityRenderBridgeImpl" ($null -ne (Has-File $src 'EntityRenderBridgeImpl.java'))
    Check "$t ItemModelRenderBridgeImpl" ($null -ne (Has-File $src 'ItemModelRenderBridgeImpl.java'))
}

Write-Output '== 现代线: 全局字符替换钩子 =='
# 钩子落在字体/字形层，按代分两种真实实现：
#   glyph-1.20-1.21.4  → FontSetMixin        （1.20.1 ~ 1.21.4 这一线）
#   glyph-1.21.5+  → FontSetSourceMixin  （1.21.11 / 26.1.2 这一线）
# 1.21.8 那两个目标没有生成树，FontSetMixin 就在自己 src 里。
# 早期那份清单断言的 MixinStringRenderOutput 已在本轮字体改造中被上述实现取代（全仓 0 个该类），
# 且旧分支用的 Mixin.*(Text|Glyph|...) 正则连 FontSetMixin 都匹配不上，故一并重写为按真实类名断言。
$fontHooks = @('FontSetMixin', 'FontSetSourceMixin')
foreach ($t in $modernTargets) {
    $tdir = Join-Path $modernRoot $t
    $roots = @(Join-Path $tdir 'src\main\java')
    $bgPath = Join-Path $tdir 'build.gradle'
    if (Test-Path $bgPath) {
        $bgText = Get-Content $bgPath -Raw
        foreach ($g in @('glyph-1.20-1.21.4', 'glyph-1.21.5+')) {
            if ($bgText -match [regex]::Escape($g)) { $roots += (Join-Path $genRoot "$g\src\main\java") }
        }
    }
    $found = @()
    foreach ($r in $roots) {
        if (-not (Test-Path $r)) { continue }
        $found += @(Get-ChildItem $r -Recurse -File -Filter '*.java' -ErrorAction SilentlyContinue |
            Where-Object { $fontHooks -contains $_.BaseName } | ForEach-Object { $_.BaseName })
    }
    $found = @($found | Sort-Object -Unique)
    Check "$t 字体替换钩子($($fontHooks -join '/'))" ($found.Count -gt 0)
}
Write-Output '== 远古线: 桥 + 全局替换 =='
foreach ($t in $legacyTargets) {
    $src = Join-Path $legacyRoot "$t\src\main\java"
    if (-not (Test-Path $src)) { Check "$t 源码目录" $false; continue }
    Check "$t LegacyEntityRenderBridgeImpl" ($null -ne (Has-File $src 'LegacyEntityRenderBridgeImpl.java'))
    Check "$t LegacyItemModelBridgeImpl" ($null -ne (Has-File $src 'LegacyItemModelBridgeImpl.java'))
    if ($t -eq 'forge-1.16.5') {
        Check "$t MixinFontGlyphs(全局替换)" ($null -ne (Has-File $src 'MixinFontGlyphs.java'))
    } else {
        Check "$t GlobalFontRenderer(全局替换)" ($null -ne (Has-File $src 'GlobalFontRenderer.java'))
    }
}

Write-Output '== mixin 注册一致性（去重 + 全量登记，堵重复注册运行时崩） =='
# 1.21.4+ 的字体/世界渲染 mixin 由共享生成源树在构建时复制进来（build/generated/<gen>），
# 不在 targets/<t>/src/main/java 下。这里按 target 的 build.gradle 实际引用了哪些生成树一起扫，
# 否则会把「已登记、文件在生成树里」误判成幽灵——本次体检即因此误报 33 处。
# 有意「有源但不登记」的例外：类名 -> (适用 target 正则, 原因)。
# target 不匹配正则却也没登记时仍然判红，防止 fabric 侧漏掉 fabric 专用 mixin。
$intentionalUnregistered = @{
    'WorldRenderPipelinesMixin' = @{
        targets = '^neoforge-'
        why     = 'fabric 专用（把自建渲染管线登记进原版静态表）；NeoForge 侧有官方管线注册事件，源码注释已说明'
    }
}
foreach ($t in $modernTargets + $legacyTargets) {
    $base = if ($modernTargets -contains $t) { $modernRoot } else { $legacyRoot }
    $tdir = Join-Path $base $t
    $src = Join-Path $tdir 'src\main\java'
    $res = Join-Path $tdir 'src\main\resources'
    if (-not (Test-Path $res)) { Check "$t mixins.json" $false; continue }
    $json = Get-ChildItem $res -Filter '*.mixins.json' -File -ErrorAction SilentlyContinue | Select-Object -First 1
    if ($null -eq $json) { Check "$t mixins.json 缺失" $false; continue }
    $cfg = Get-Content $json.FullName -Raw -Encoding UTF8 | ConvertFrom-Json
    $reg = @()
    foreach ($k in @('client', 'mixins', 'server', 'common')) {
        if ($null -ne $cfg.$k) { $reg += @($cfg.$k) }
    }
    # 可扫的源根 = 本 target 的 src + 它 build.gradle 引用到的共享生成树
    $scanRoots = @($src)
    $bgPath = Join-Path $tdir 'build.gradle'
    if (Test-Path $bgPath) {
        $bgText = Get-Content $bgPath -Raw
        foreach ($g in $genDirs) {
            if ($bgText -match [regex]::Escape($g)) {
                $gsrc = Join-Path $genRoot "$g\src\main\java"
                if (Test-Path $gsrc) { $scanRoots += $gsrc }
                else { Check "$t 引用的生成树 $g 源缺失" $false }
            }
        }
    }
    $files = @()
    foreach ($r in $scanRoots) {
        $files += @(Get-ChildItem $r -Recurse -File -Filter '*.java' -ErrorAction SilentlyContinue |
            Where-Object { $_.Directory.Name -eq 'mixin' } | ForEach-Object { $_.BaseName })
    }
    $files = @($files | Sort-Object -Unique)
    $regu = @($reg | Sort-Object -Unique)
    $dup = @($reg | Group-Object | Where-Object Count -gt 1)
    $missing = @()
    foreach ($n in @($files | Where-Object { $_ -notin $regu })) {
        $ex = $intentionalUnregistered[$n]
        if ($null -ne $ex -and $t -match $ex.targets) {
            Write-Output "  [--] $t 有意不登记 $n （$($ex.why)）"
        } else {
            $missing += $n
        }
    }
    $ghost = @($regu | Where-Object { $_ -notin $files })
    $ok = $dup.Count -eq 0 -and $missing.Count -eq 0 -and $ghost.Count -eq 0
    Check "$t mixin 注册一致($($files.Count))" $ok
    if (-not $ok) {
        if ($dup.Count -gt 0) { Check "  $t 重复注册 $($dup.Name -join ',')" $false }
        if ($missing.Count -gt 0) { Check "  $t 遗漏登记 $($missing -join ',')" $false }
        if ($ghost.Count -gt 0) { Check "  $t 幽灵登记 $($ghost -join ',')" $false }
    }
}
Write-Output ''
if ($fail -eq 0) {
    Write-Output "SYNC CHECK PASS ($($modernTargets.Count) modern + $($legacyTargets.Count) legacy)"
    exit 0
} else {
    Write-Output "SYNC CHECK FAIL: $fail 处缺口"
    exit 1
}
