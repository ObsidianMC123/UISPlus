$ErrorActionPreference = 'Stop'
$dst = 'D:\mcproject\UISApp\app\src\main\res\font'
New-Item -ItemType Directory -Force $dst | Out-Null
$tmp = 'D:\mcproject\UISApp\tools\inter_tmp'
New-Item -ItemType Directory -Force $tmp | Out-Null
$zip = "$tmp\inter.zip"
if (-not (Test-Path $zip)) { Invoke-WebRequest 'https://github.com/rsms/inter/releases/download/v4.1/Inter-4.1.zip' -OutFile $zip -UseBasicParsing }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$z = [System.IO.Compression.ZipFile]::OpenRead($zip)
$want = @{ 'Inter-Regular.ttf'='inter_regular.ttf'; 'Inter-Medium.ttf'='inter_medium.ttf'; 'Inter-SemiBold.ttf'='inter_semibold.ttf'; 'Inter-Bold.ttf'='inter_bold.ttf'; 'Inter-ExtraBold.ttf'='inter_extrabold.ttf' }
foreach ($e in $z.Entries) {
  if ($e.FullName -like 'extras/ttf/*' -and $want.ContainsKey($e.Name)) {
    [System.IO.Compression.ZipFileExtensions]::ExtractToFile($e, (Join-Path $dst $want[$e.Name]), $true)
  }
}
$z.Dispose()
Get-ChildItem $dst | ForEach-Object { $_.Name + ' ' + $_.Length }
