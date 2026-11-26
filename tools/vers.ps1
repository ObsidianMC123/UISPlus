$u = @(
 'https://repo1.maven.org/maven2/org/robolectric/robolectric/maven-metadata.xml',
 'https://repo1.maven.org/maven2/io/github/takahirom/roborazzi/roborazzi/maven-metadata.xml',
 'https://repo1.maven.org/maven2/io/github/takahirom/roborazzi/roborazzi-compose/maven-metadata.xml',
 'https://repo1.maven.org/maven2/org/robolectric/android-all-instrumented/maven-metadata.xml'
)
foreach ($x in $u) {
  try {
    $c = (Invoke-WebRequest $x -UseBasicParsing -TimeoutSec 30).Content
    $v = ([xml]$c).metadata.versioning.versions.version | Where-Object { $_ -notmatch 'alpha|beta|rc|SNAPSHOT' }
    Write-Output ($x.Split('/')[-2] + ' => ' + (($v | Select-Object -Last 6) -join ', '))
  } catch { Write-Output "$x ERR $_" }
}
