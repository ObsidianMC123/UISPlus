$c = (Invoke-WebRequest 'https://dl.google.com/android/repository/repository2-3.xml' -UseBasicParsing -TimeoutSec 60).Content
[regex]::Matches($c, 'commandlinetools-win-[0-9]+_latest\.zip') | ForEach-Object Value | Select-Object -Unique
[regex]::Matches($c, 'path="(platforms;android-[0-9.A-Za-z]+|build-tools;[0-9.]+|platform-tools)"') | ForEach-Object { $_.Groups[1].Value } | Select-Object -Unique
