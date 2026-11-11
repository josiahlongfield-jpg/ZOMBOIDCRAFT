# Copies the Tarkov melee bundles listed in melee-list.txt into tarkov-pilot\raw with flat names (read-only on the game folder).
$src = 'C:\Program Files (x86)\Steam\steamapps\common\Escape from Tarkov\build\EscapeFromTarkov_Data\StreamingAssets\Windows'
$tp  = 'C:\Attachments\OneDrive\Desktop\additions\tarkov-pilot'
$out = Join-Path $tp 'raw'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$sizes = Join-Path $tp 'melee-sizes.txt'
'' | Set-Content $sizes
foreach ($rel in Get-Content (Join-Path $tp 'melee-list.txt')) {
  if (-not $rel) { continue }
  $f = Join-Path $src ($rel -replace '/','\')
  $flat = 'EscapeFromTarkov_Data__StreamingAssets__Windows__' + ($rel -replace '/','__')
  if (Test-Path -LiteralPath $f) { Copy-Item -LiteralPath $f -Destination (Join-Path $out $flat) -Force; "$flat`t$((Get-Item -LiteralPath $f).Length)" | Add-Content $sizes }
  else { "MISSING $rel" | Add-Content $sizes }
}
'DONE' | Add-Content $sizes
