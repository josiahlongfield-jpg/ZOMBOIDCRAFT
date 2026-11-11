# Copies the batch 3 M16A2 parts into the raw folder (flat names), and writes their sizes.
$src = 'C:\Program Files (x86)\Steam\steamapps\common\Escape from Tarkov\build\EscapeFromTarkov_Data\StreamingAssets\Windows'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$dst = Join-Path $here 'raw'
$sizes = @()
$miss = 0
foreach ($rel in Get-Content (Join-Path $here 'batch3-m16-list.txt')) {
  if (-not $rel.Trim()) { continue }
  $from = Join-Path $src ($rel -replace '/', '\')
  $name = 'EscapeFromTarkov_Data__StreamingAssets__Windows__' + ($rel -replace '/', '__')
  if (Test-Path -LiteralPath $from) {
    Copy-Item -LiteralPath $from -Destination (Join-Path $dst $name) -Force
    $sizes += "$name`t$((Get-Item -LiteralPath $from).Length)"
  } else { $miss++; Write-Host "missing $rel" }
}
$sizes | Set-Content (Join-Path $here 'batch3-m16-sizes.txt') -Encoding utf8
Write-Host "copied $($sizes.Count), missing $miss"
