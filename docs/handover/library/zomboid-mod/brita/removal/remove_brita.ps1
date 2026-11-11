# Takes every Brita gun and his pack sights/grips out of ZomboidCraft (Josiah 2026-11-07 10:32).
# Nothing is deleted: files move to additions\brita-archive-20261107. Each Brita gun's json becomes a
# {"id":..,"removed":true} marker so copies already in the world (and their magazines) disappear.
$ErrorActionPreference = 'Continue'
$prof = 'C:\Users\josia\AppData\Roaming\ModrinthApp\profiles\MEDIEVAL GLOOM (1)'
$g    = Join-Path $prof 'config\zomboidcraft\guns'
$arc  = 'C:\Attachments\OneDrive\Desktop\additions\brita-archive-20261107'
$log  = 'C:\Attachments\OneDrive\Desktop\additions\brita-archive-20261107.log'
'' | Set-Content $log
function L($m) { $m | Add-Content $log; Write-Output $m }
foreach ($d in 'guns','guns\poses','guns\reloads','resourcepacks') { New-Item -ItemType Directory -Force -Path (Join-Path $arc $d) | Out-Null }
function Mv($src, $dstDir) {
  if (Test-Path -LiteralPath $src) {
    try { Move-Item -LiteralPath $src -Destination (Join-Path $arc $dstDir) -Force -ErrorAction Stop; L "moved $src" }
    catch { L "FAILED $src : $($_.Exception.Message)" }
  }
}
$ids = 'AAC_Honey','American180','AR57_PDW','AssaultRifle2','BOAR','Bush_AR15_MOE','Colt1911','DSHK','L86','M2Carbine','M870_CQB','RPG_7','SW_M500','TenPointTurbo_XLT','Winchester1866'
foreach ($id in $ids) {
  Mv (Join-Path $g "$id.json") 'guns'
  Mv (Join-Path $g "$id.zmesh") 'guns'
  Mv (Join-Path $g "${id}_noirons.zmesh") 'guns'
  Mv (Join-Path $g "poses\$id.json") 'guns\poses'
  Mv (Join-Path $g "reloads\$id.json") 'guns\reloads'
  Mv (Join-Path $g "reloads\$id.cues.json") 'guns\reloads'
  $marker = '{"id":"' + $id + '","removed":true}'
  [System.IO.File]::WriteAllText((Join-Path $g "$id.json"), $marker)
  L "marker $id"
}
# his pack sights and Brita's grips (guns\parts): the whole folder goes
Mv (Join-Path $g 'parts') 'guns'
# the Brita sound pack
Mv (Join-Path $prof 'resourcepacks\ZomboidCraft Brita Sounds') 'resourcepacks'
$poses = Join-Path $g 'poses'
if ((Test-Path $poses) -and -not (Get-ChildItem $poses)) { Remove-Item $poses; L 'removed empty poses folder' }
L '--- left in guns:'
Get-ChildItem -Recurse $g | ForEach-Object { L ($_.FullName.Substring($g.Length)) }
L 'DONE'
