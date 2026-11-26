# Tai Android SDK dang PORTABLE vao D:\mcproject\UISApp\_sdk (khong installer, khong registry).
# Xoa thu muc _sdk la go sach.
$ErrorActionPreference = 'Stop'
$sdk = 'D:\mcproject\UISApp\_sdk'
New-Item -ItemType Directory -Force $sdk | Out-Null
$zip = Join-Path $sdk 'cmdline-tools.zip'
if (-not (Test-Path "$sdk\cmdline-tools\latest\bin\sdkmanager.bat")) {
  if (Test-Path $zip) { Remove-Item $zip -Force }
  & curl.exe -sSL -o $zip 'https://dl.google.com/android/repository/commandlinetools-win-16111833_latest.zip'
  if ($LASTEXITCODE -ne 0) { throw "curl failed $LASTEXITCODE" }
  Expand-Archive $zip -DestinationPath "$sdk\tmp" -Force
  New-Item -ItemType Directory -Force "$sdk\cmdline-tools" | Out-Null
  Move-Item "$sdk\tmp\cmdline-tools" "$sdk\cmdline-tools\latest"
  Remove-Item "$sdk\tmp" -Recurse -Force; Remove-Item $zip -Force
}
$env:JAVA_HOME = 'C:\Program Files\Java\jdk-25.0.4'
$sm = "$sdk\cmdline-tools\latest\bin\sdkmanager.bat"
$yes = ('y' + [Environment]::NewLine) * 30
$yes | & $sm --sdk_root=$sdk --licenses | Select-Object -Last 3
& $sm --sdk_root=$sdk 'platforms;android-37.0' 'build-tools;37.0.0' 'platform-tools' | Select-Object -Last 5
Get-ChildItem $sdk | ForEach-Object Name
