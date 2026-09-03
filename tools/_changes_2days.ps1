# =====================================================================
# DMS - 昨天+今天 两天变更文件枚举 + 打包（零输入，双击 bat 运行）
# 规则：
#   1. 按文件修改时间筛选"昨天 00:00 ~ 明天 00:00"新增/变更的文件
#   2. 排除构建产物/依赖/.git/压缩包与清单自身
#   3. 清单写 docs/09_测试报告/变更清单_<起>-<止>.md
#   4. 新增+变更文件按原目录结构打包为项目根目录 dms-changes-<起>-<止>.zip
# =====================================================================
$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
$OutputEncoding         = [System.Text.Encoding]::UTF8

$ROOT    = Split-Path -Parent $PSScriptRoot
$START   = (Get-Date).Date.AddDays(-1)     # 昨天 00:00
$END     = (Get-Date).Date.AddDays(1)      # 明天 00:00（窗口右开）
$STARTSTR= $START.ToString('yyyyMMdd')
$ENDSTR  = (Get-Date).Date.ToString('yyyyMMdd')
$RANGE   = "$STARTSTR-$ENDSTR"
$ZIP     = Join-Path $ROOT "dms-changes-$RANGE.zip"
$LIST    = Join-Path $ROOT "docs\09_测试报告\变更清单_$RANGE.md"
$LISTNAME= Split-Path -Leaf $LIST
$ZIPNAME = Split-Path -Leaf $ZIP

Write-Host "项目根目录 : $ROOT"
Write-Host "统计窗口   : $($START.ToString('yyyy-MM-dd 00:00')) ~ $((Get-Date).Date.ToString('yyyy-MM-dd 24:00'))"
Write-Host ""

$excludeDirs = @('.git','node_modules','target','dist','.m2','.idea','.vscode',
                 'maven-repo','npm-cache','build','.gradle','logs','log',
                 '__pycache__','.cache','coverage','.nuxt','.output')
$excludeFiles= @('dms-backend.tar.gz','pscp.exe','plink.exe')
$script:hitFiles = New-Object System.Collections.Generic.List[object]

function Walk($dir) {
    foreach ($f in [System.IO.Directory]::EnumerateFiles($dir)) {
        try {
            $fi = Get-Item -LiteralPath $f -Force
            if ($fi.LastWriteTime -ge $START -and $fi.LastWriteTime -lt $END) {
                if ($excludeFiles -contains $fi.Name) { continue }
                if ($fi.Name -like 'dms-changes-*.zip') { continue }
                if ($fi.Name -eq $LISTNAME) { continue }
                $script:hitFiles.Add($fi)
            }
        } catch {}
    }
    foreach ($d in [System.IO.Directory]::EnumerateDirectories($dir)) {
        $name = Split-Path $d -Leaf
        if ($excludeDirs -contains $name) { continue }
        Walk $d
    }
}
Write-Host "[1/5] 扫描两天内变更文件 ..."
Walk $ROOT

$hitFiles = @($hitFiles | Sort-Object FullName -Unique)
Write-Host "      两天内新增/变更文件：$($hitFiles.Count) 个"

$manualDeleted = @(
    'tools\_dms_deploy.log',
    'tools\_dms_deploy_test.bat',
    'tools\_dms_remote_build.sh',
    'tools\_cu_probe.log',
    'tools\_cu_probe.bat',
    'tools\_cu_build.log',
    'tools\_cu_build_check.bat'
)
$allDeleted = @($manualDeleted | Where-Object { -not (Test-Path (Join-Path $ROOT $_)) }) | Sort-Object -Unique

Write-Host "[2/5] 生成变更清单 ..."
function FmtSize($b) {
    if ($b -ge 1MB) { return ('{0:N1} MB' -f ($b/1MB)) }
    if ($b -ge 1KB) { return ('{0:N1} KB' -f ($b/1KB)) }
    return "$b B"
}
$sb = New-Object System.Text.StringBuilder
[void]$sb.AppendLine("# DMS 项目变更清单 - $($START.ToString('yyyy-MM-dd')) ~ $((Get-Date).Date.ToString('yyyy-MM-dd'))")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("- 统计范围：项目根目录下昨天 00:00 至今天 24:00 新增/变更的文件（已排除 node_modules / target / dist / .git / .m2 等构建产物与依赖目录，以及 dms-backend.tar.gz / *.exe / dms-changes-*.zip 等二进制与打包产物）")
[void]$sb.AppendLine("- 新增+变更文件已打包：``dms-changes-$RANGE.zip``（项目根目录，保留原目录结构，本清单已含在包内）")
[void]$sb.AppendLine("- 删除文件仅登记不打包")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("## 一、统计概览")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("| 类别 | 数量 |")
[void]$sb.AppendLine("|------|------|")
[void]$sb.AppendLine("| 新增/变更（已打包） | $($hitFiles.Count) |")
[void]$sb.AppendLine("| 删除（仅登记） | $($allDeleted.Count) |")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("## 二、新增 / 变更文件清单（$($hitFiles.Count) 个，已打包）")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("| # | 相对路径 | 大小 | 最后修改时间 |")
[void]$sb.AppendLine("|---|----------|------|--------------|")
$i = 0
foreach ($f in $hitFiles) {
    $i++
    $rel = $f.FullName.Substring($ROOT.Length + 1)
    [void]$sb.AppendLine("| $i | ``$rel`` | $(FmtSize $f.Length) | $($f.LastWriteTime.ToString('yyyy-MM-dd HH:mm:ss')) |")
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
        [void]$sb.AppendLine("| $j | ``$d`` | 09-02 部署清理的临时文件 |")
    }
} else {
    [void]$sb.AppendLine("（无）")
}
[void]$sb.AppendLine("")
[void]$sb.AppendLine("## 四、说明")
[void]$sb.AppendLine("")
[void]$sb.AppendLine("- 「新增/变更」依据文件系统最后修改时间判定（修改时间无法区分新增与修改，故合并列示；git 未跟踪的新文件同样包含）")
[void]$sb.AppendLine("- 两天工作内容：09-02 v4.5.5 外部经销商开放协同（openapi 包 + V143/V144/V145）与 v4.6.1 定时邮件开关（V146 + 邮件服务/控制器 + admin-vue 通知设置页）+ 文档回写；09-03 生产环境推送 v4.6.1（Flyway V143->V146）、本地 MCP/SSH 通道修复（tools/_mcp_fix）、版本文档回写")
[void]$sb.AppendLine("- 压缩包内文件保持项目相对目录结构，解压后可直接覆盖回项目根目录")
[void]$sb.AppendLine("- 生成工具：``tools/_changes_2days.ps1``（由根目录 bat 双击调用）")
[void]$sb.AppendLine("")

$listDir = Split-Path -Parent $LIST
if (-not (Test-Path $listDir)) { New-Item -ItemType Directory -Path $listDir -Force | Out-Null }
[System.IO.File]::WriteAllText($LIST, $sb.ToString(), (New-Object System.Text.UTF8Encoding($true)))
Write-Host "      清单已生成：$LIST"

Write-Host "[3/5] 暂存待打包文件 ..."
$stage = Join-Path $env:TEMP "dms-changes-stage-$RANGE"
if (Test-Path $stage) { Remove-Item -LiteralPath $stage -Recurse -Force }
New-Item -ItemType Directory -Path $stage -Force | Out-Null
foreach ($f in $hitFiles) {
    $rel = $f.FullName.Substring($ROOT.Length + 1)
    $dst = Join-Path $stage $rel
    $dstDir = Split-Path -Parent $dst
    if (-not (Test-Path $dstDir)) { New-Item -ItemType Directory -Path $dstDir -Force | Out-Null }
    Copy-Item -LiteralPath $f.FullName -Destination $dst -Force
}
Copy-Item -LiteralPath $LIST -Destination (Join-Path $stage $LISTNAME) -Force

Write-Host "[4/5] 生成压缩包 ..."
if (Test-Path $ZIP) { Remove-Item -LiteralPath $ZIP -Force }
Add-Type -AssemblyName System.IO.Compression.FileSystem
[System.IO.Compression.ZipFile]::CreateFromDirectory($stage, $ZIP,
    [System.IO.Compression.CompressionLevel]::Optimal, $false)
Remove-Item -LiteralPath $stage -Recurse -Force

$zipInfo = Get-Item -LiteralPath $ZIP
Write-Host "      压缩包：$ZIP ($(FmtSize $zipInfo.Length))"

Write-Host "[5/5] 校验压缩包内容 ..."
$zip = [System.IO.Compression.ZipFile]::OpenRead($ZIP)
$entryCount = $zip.Entries.Count
$zip.Dispose()
Write-Host "      包内条目数：$entryCount（含清单文件）"
Write-Host ""
Write-Host "================ 完成 ================"
Write-Host "清单文件: $LIST"
Write-Host "压缩包  : $ZIP ($(FmtSize $zipInfo.Length))"
Write-Host "打包文件: $($hitFiles.Count) 个；删除登记: $($allDeleted.Count) 个"
Write-Host "======================================"
