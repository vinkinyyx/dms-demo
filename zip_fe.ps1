if (Test-Path automation_test\frontend-dist.zip) { Remove-Item automation_test\frontend-dist.zip -Force }
Compress-Archive -Path frontend-vue\dist\* -DestinationPath automation_test\frontend-dist.zip -Force
Write-Host "zipped" (Get-Item automation_test\frontend-dist.zip).Length
