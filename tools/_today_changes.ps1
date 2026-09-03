# =====================================================================
# DMS - 今日变更文件枚举 + 打包（零输入，双击 _today_changes.bat 运行）
# 规则：
#   1. 按文件修改时间筛选"今天"（00:00 ~ 次日 00:00）新增/变更的文件
#   2. 排除构建产物/依赖/.git/本次生成的压缩包与清单本身
#   3. 删除文件：git 仓库时读取 git status 的已删除项；并附注已知临时删除
#   4. 清单写 docs/09_测试报告/变更清单_20260902.md（同时输出控制台）
#   5. 新增+变更文件按原目录结构打包为项目根目录 dms-changes-20260902.zip
# =====================================================================
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding         = [System.Text.Encoding]::UTF8

$ROOT    = Split-Path -Parent $PSScriptRoot          # 项目根目录（tools 的上一级）
$TODAY   = (Get-Date).Date                            # 今天 00:00
$TOMORROW= $TODAY.AddDays(1)
$DATESTR = $TODAY.ToString('yyyyMMdd')
$ZIP     = Join-Path $ROOT "dms-changes-$DATESTR.zip"
$LIST    = Join-Path $ROOT "docs\09_测试报告\变更清单_$DATESTR.md"

Write-Host "项目根目录 : $ROOT"
Write-Host "统计日期   : $($TODAY.ToString('yyyy-MM-dd'))"
Write-Host ""

# ---------- 1. 递归枚举今日修改的文件（剪枝排除大目录） ----------
$excludeDirs = @('.git','node_modules','target','dist','.m2','.idea','.vscode',
                 'maven-repo','npm-cache','build','.gradle','logs','log',
                 '__pycache__','.cache','coverage','.nuxt','.output')
$script:todayFiles = New-Object System.Collections.Generic.List[object]

function Walk($dir) {
    foreach ($f in [System.IO.Directory]::EnumerateFiles($dir)) {
        try {
            $fi = Get-Item -LiteralPath $f -Force
            if ($fi.LastWriteTime -ge $TODAY -and $fi.LastWriteTime -lt $TOMORROW) {
                $script:todayFiles.Add($fi)
            }
        } catch {}
    }
    foreach ($d in [System.IO.Directory]::EnumerateDirectories($dir)) {
        $name = Split-Path $d -Leaf
        if ($excludeDirs -contains $name) { continue }
        Walk $d
    }
}
Write-Host "[1/5] 扫描今日变更文件 ..."
Walk $ROOT

# 排除本次产物自身（zip / 清单），避免自引用
$excludeSuffix = @("dms-changes-$DATESTR.zip", "变更清单_$DATESTR.md")
$todayFiles = @($todayFiles | Where-Object {
        $rel = $_.FullName.Substring($ROOT.Length + 1)
        $skip = $false
        foreach ($s in $excludeSuffix) { if ($_.Name -eq $s) { $skip = $true } }
        -not $skip
    } | Sort-Object FullName -Unique)

Write-Host "      今日新增/变更文件：$($todayFiles.Count) 个"

# ---------- 2. git 读取删除/新增分类（若为 git 仓库） ----------
$gitDeleted = New-Object System.Collections.Generic.List[string]
$gitAdded   = New-Object System.Collections.Generic.List[string]
$gitModified= New-Object System.Collections.Generic.List[string]
$isGit = $false
try {
    Push-Location $ROOT
    $gitCheck = & git rev-parse --is-inside-work-tree 2>$null
    if ($LASTEXITCODE -eq 0 -and "$gitCheck".Trim() -eq 'true') {
        $isGit = $true
        $lines = & git status --porcelain --untracked-files=all 2>$null
        foreach ($ln in $lines) {
            if (-not $ln) { continue }
            $xy = $ln.Substring(0,2)
            $path = $ln.Substring(3)
            if ($path -match ' -> ') { $path = ($path -split ' -> ')[-1] }
            $path = $path.Trim('"') -replace '/','\'
            $x = $xy.Substring(0,1); $y = $xy.Substring(1,1)
            if ($x -eq 'D' -or $y -eq 'D') { $gitDeleted.Add($path) }
            elseif ($x -eq '?' -and $y -eq '?') { $gitAdded.Add($path) }
            else { $gitModified.Add($path) }
        }
    }
    Pop-Location
} catch { try { Pop-Location } catch {} }

# 已知的今日删除（本会话清理的临时部署文件，已不在文件系统中）
$manualDeleted = @(
    'tools\_dms_deploy.log',
    'tools\_dms_deploy_test.bat',
    'tools\_dms_remote_build.sh',
    'tools\_cu_probe.log',
    'tools\_cu_probe.bat',
    'tools\_cu_build.log',
    'tools\_cu_build_check.bat'
)
$allDeleted = @($gitDeleted + ($manualDeleted | Where-Object { -not (Test-Path (Join-Path $ROOT $_)) })) | Sort-Object -Unique

# ---------- 3. 生成 Markdown 清单 ----------
Write-Host "[2/5] 生成变更清单 ..."
function FmtSize($b) {
    if ($b -ge 1MB) { return ('{0:N1} MB' -f ($b/1MB)) }
    if ($b -ge 1KB) { return ('{0:N1} KB' -f ($b/1KB)) }
    return "$b B"
}
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("# DMS 项目变更清单 - $($TODAY.ToString('yyyy-MM-dd'))")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("- 统计范围：项目根目录 `$ROOT` 下当日 00:00 起新增/变更/删除的文件（已排除 node_modules / target / dist / .git / .m2 等构建产物与依赖目录）")
[void]$sb.AppendLine("- 新增+变更文件已打包：``dms-changes-$DATESTR.zip``（项目根目录，保留原目录结构，清单文件已含在包内）")
[void]$sb.AppendLine("- 删除文件仅登记不打包")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("## 一、统计概览")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("| 类别 | 数量 |")
[void]$sb.AppendLine("|------|------|")
[void]$sb.AppendLine("| 新增/变更（已打包） | $($todayFiles.Count) |")
[void]$sb.AppendLine("| 删除（仅登记） | $($allDeleted.Count) |")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("## 二、新增 / 变更文件清单（$($todayFiles.Count) 个，已打包）")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("| # | 相对路径 | 大小 | 最后修改时间 |")
[void]$sb.AppendLine("|---|----------|------|--------------|")
$i = 0
foreach ($f in $todayFiles) {
    $i++
    $rel = $f.FullName.Substring($ROOT.Length + 1)
    [void]$sb.AppendLine("| $i | ``$rel`` | $(FmtSize $f.Length) | $($f.LastWriteTime.ToString('HH:mm:ss')) |")
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("## 三、删除文件清单（$($allDeleted.Count) 个，仅登记）")
[void]$sb.AppendLine("")
if ($allDeleted.Count -gt 0) {
    [void]$sb.AppendLine("| # | 相对路径 | 来源 |")
    [void]$sb.AppendLine("|---|----------|------|")
    $j = 0
    foreach ($d in $allDeleted) {
        $j++
        $src = if ($gitDeleted -contains $d) { 'git 记录' } else { '本次清理的临时部署文件' }
        [void]$sb.AppendLine("| $j | ``$d`` | $src |")
    }
} else {
    [void]$sb.AppendLine("（无）")
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("## 四、说明")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("- 「新增/变更」依据文件系统最后修改时间判定（修改时间无法区分新增与修改，故合并列示；git 未跟踪的新文件同样包含）")
[void]$sb.AppendLine("- 压缩包内文件保持项目相对目录结构，解压后可直接覆盖回项目根目录")
[void]$sb.AppendLine("- 生成工具：``tools/_today_changes.ps1``（由 ``tools/_today_changes.bat`` 双击调用）")
[void]$sb.AppendLine("")

$listDir = Split-Path -Parent $LIST
if (-not (Test-Path $listDir)) { New-Item -ItemType Directory -Path $listDir -Force | Out-Null }
[System.IO.File]::WriteAllText($LIST, $sb.ToString(), (New-Object System.Text.UTF8Encoding($true)))
Write-Host "      清单已生成：$LIST"

# ---------- 4. 暂存 + 压缩 ----------
Write-Host "[3/5] 暂存待打包文件 ..."
$stage = Join-Path $env:TEMP "dms-changes-stage-$DATESTR"
if (Test-Path $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage -Force | Out-Null
foreach ($f in $todayFiles) {
    $rel = $f.FullName.Substring($ROOT.Length + 1)
    $dst = Join-Path $stage $rel
    $dstDir = Split-Path -Parent $dst
    if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Path $dstDir -Force | Out-Null }
    Copy-Item -LiteralPath $f.FullName -Destination $dst -Force
}
# 清单也放进压缩包根目录
Copy-Item -LiteralPath $LIST -Destination (Join-Path $stage (Split-Path -Leaf $LIST)) -Force

Write-Host "[4/5] 生成压缩包 ..."
if (Test-Path $ZIP) { Remove-Item -LiteralPath $ZIP -Force }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($stage, $ZIP,
    [System.IO.Compression.CompressionLevel]::Optimal, $false)
Remove-Item -LiteralPath $stage -Recurse -Force

$zipInfo = Get-Item -LiteralPath $ZIP
Write-Host "      压缩包：$ZIP ($(FmtSize $zipInfo.Length))"

# ---------- 5. 校验压缩包内容 ----------
Write-Host "[5/5] 校验压缩包内容 ..."
Add-Type -AssemblyName System.IO.Compression.FileSystem
$zip = [System.IO.Compression.ZipFile]::OpenRead($ZIP)
$entryCount = $zip.Entries.Count
$zip.Dispose()
Write-Host "      包内条目数：$entryCount（含清单文件）"
Write-Host ""
Write-Host "================ 完成 ================"
Write-Host "清单文件: $LIST"
Write-Host "压缩包  : $ZIP ($(FmtSize $zipInfo.Length))"
Write-Host "打包文件: $($todayFiles.Count) 个；删除登记: $($allDeleted.Count) 个"
Write-Host "======================================"
