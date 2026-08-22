$rel = "DMS_v4.0.0-bugfix.1_release"
$relFull = (Resolve-Path $rel).Path
Get-ChildItem -Path (Join-Path $relFull "backend") -Recurse -File | ForEach-Object {
  $dest = $_.FullName.Substring($relFull.Length+1)
  $target = Join-Path (Get-Location) $dest
  New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
  Copy-Item $_.FullName $target -Force
}
Get-ChildItem -Path (Join-Path $relFull "admin-vue") -Recurse -File | ForEach-Object {
  $dest = $_.FullName.Substring($relFull.Length+1)
  $target = Join-Path (Get-Location) $dest
  New-Item -ItemType Directory -Path (Split-Path $target) -Force | Out-Null
  Copy-Item $_.FullName $target -Force
}
Copy-Item (Join-Path $relFull "frontend-vue\Dockerfile.test") "frontend-vue\Dockerfile.test" -Force
Write-Host "backend + admin copied"
$del = @(
 "backend\src\main\java\com\dms\masterdata\controller\ProductPackageLevelController.java",
 "backend\src\main\java\com\dms\masterdata\entity\ProductPackageLevel.java",
 "backend\src\main\java\com\dms\masterdata\repository\ProductPackageLevelRepository.java",
 "backend\src\main\java\com\dms\masterdata\service\ProductPackageLevelService.java"
)
foreach ($d in $del) { if (Test-Path $d) { Remove-Item $d -Force; Write-Host "deleted $d" } }
