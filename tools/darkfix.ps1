$ErrorActionPreference = 'Stop'
$root = 'D:\mcproject\UISApp\app\src\main\java\vn\uisplus\ui'
$utf8 = New-Object System.Text.UTF8Encoding($false)
$rules = @(
  @('.background(Color.White)', '.background(Ui.Card)'),
  @('containerColor = Color.White,', 'containerColor = Ui.Card,'),
  @('Color(0xFFF3F4F8)', 'Ui.Soft'),
  @('Color(0xFFF1F2F6)', 'Ui.Soft'),
  @('Color(0xFFF3F4F6)', 'Ui.Soft'),
  @('Color(0xFFFAFAFC)', 'Ui.Soft'),
  @('if (selected) Ui.Brand else Color.White', 'if (selected) Ui.Brand else Ui.Card'),
  @('lerp(c, Color.Black, 0.35f)', 'lerp(c, Ui.Contrast, 0.35f)'),
  @('lerp(c, Color.Black, 0.2f)', 'lerp(c, Ui.Contrast, 0.2f)'),
  @('.background(Color.White.copy(alpha = 0.92f))', '.background(Ui.Card.copy(alpha = 0.92f))'),
  @('.border(1.dp, Color.White, RoundedCornerShape(28.dp))', '.border(1.dp, Ui.Card, RoundedCornerShape(28.dp))'),
  @('focusedContainerColor = Color.White,', 'focusedContainerColor = Ui.Card,'),
  @('containerColor = Ui.Ink, contentColor = Color.White', 'containerColor = Ui.Ink, contentColor = Ui.Bg')
)
foreach ($f in Get-ChildItem $root -Filter *.kt) {
  $t = [IO.File]::ReadAllText($f.FullName, $utf8)
  $o = $t
  foreach ($r in $rules) { $t = $t.Replace($r[0], $r[1]) }
  if ($t -ne $o) {
    [IO.File]::WriteAllText($f.FullName, $t, $utf8)
    Write-Output ("changed " + $f.Name)
  }
}
Select-String -Path "$root\*.kt" -Pattern 'Color\.White\)|Color\(0xFF' | % { '{0}:{1}: {2}' -f $_.Filename,$_.LineNumber,$_.Line.Trim() }
