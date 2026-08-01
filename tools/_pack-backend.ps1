param([string]$Version = "v3.7.3")
$ErrorActionPreference = "Stop"
Add-Type -AssemblyName System.IO.Compression.FileSystem

$zipPath = Join-Path $env:TEMP "backend-src-$Version.zip"
if (Test-Path $zipPath) { Remove-Item $zipPath -Force }

$baseDir = (Get-Item "d:\Workspace\TRAE\DMS\backend").FullName
$files = Get-ChildItem -Path $baseDir -Recurse -File | Where-Object {
    $_.FullName -notmatch '[\\/]target[\\/]' -and
    $_.FullName -notmatch '[\\/]\.git[\\/]'
} | ForEach-Object {
    $rel = $_.FullName.Substring($baseDir.Length + 1) -replace '\\', '/'
    $rel
}

$zip = [System.IO.Compression.ZipFile]::Open($zipPath, 'Create')
try {
    foreach ($f in $files) {
        $full = Join-Path $baseDir $f
        [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($zip, $full, $f, [System.IO.Compression.CompressionLevel]::Optimal) | Out-Null
    }
} finally {
    $zip.Dispose()
}

Write-Host "Created $zipPath with $($files.Count) files"
Get-Item $zipPath | Select-Object Name, Length
