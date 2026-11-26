$ErrorActionPreference = 'Stop'
$root = 'D:\mcproject\UISApp'
$ks = "$root\keystore\uisplus-release.jks"
if (Test-Path $ks) { Write-Output 'keystore da co, bo qua'; exit 0 }
New-Item -ItemType Directory -Force "$root\keystore" | Out-Null
$bytes = New-Object byte[] 24
[System.Security.Cryptography.RandomNumberGenerator]::Create().GetBytes($bytes)
$pw = ([Convert]::ToBase64String($bytes)) -replace '[^A-Za-z0-9]', 'x'
$ErrorActionPreference = 'Continue'
& 'C:\Program Files\Java\jdk-25.0.4\bin\keytool.exe' -genkeypair -keystore $ks -storetype PKCS12 -alias uisplus -keyalg RSA -keysize 3072 -validity 10000 -storepass $pw -keypass $pw -dname 'CN=UIS Plus, OU=Personal, O=UISPlus, C=VN' 2>$null
if (-not (Test-Path $ks)) { throw 'keytool khong tao duoc keystore' }
$ErrorActionPreference = 'Stop'
@"
storeFile=keystore/uisplus-release.jks
storePassword=$pw
keyAlias=uisplus
keyPassword=$pw
"@ | Set-Content -Encoding ASCII "$root\keystore.properties"
Write-Output 'OK keystore + keystore.properties'
