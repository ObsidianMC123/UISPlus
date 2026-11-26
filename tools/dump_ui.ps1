$ErrorActionPreference = 'Stop'
Set-Location D:\mcproject\UISApp
Compress-Archive -Force -Path app\src -DestinationPath tools\bak\src_before_v140.zip
$p = @('app\src\main\java\vn\uisplus\ui','app\src\main\java\vn\uisplus\MainActivity.kt','app\build.gradle.kts','app\src\test','app\src\main\java\vn\uisplus\data\Settings.kt','app\src\main\java\vn\uisplus\data\SubjectColors.kt','app\src\main\java\vn\uisplus\data\Models.kt','app\src\main\java\vn\uisplus\focus','gradle\libs.versions.toml')
$p = $p | Where-Object { Test-Path $_ }
Compress-Archive -Force -Path $p -DestinationPath D:\mcproject\_ui_dump.zip
Get-Item tools\bak\src_before_v140.zip, D:\mcproject\_ui_dump.zip | ForEach-Object { "$($_.Length) $($_.FullName)" }
