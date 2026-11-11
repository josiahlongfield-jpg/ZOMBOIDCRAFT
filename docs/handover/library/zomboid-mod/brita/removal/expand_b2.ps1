# Copies Tarkov batch 2 into the profile (guns, bench parts, sounds). Overwrites same-named files.
$prof = 'C:\Users\josia\AppData\Roaming\ModrinthApp\profiles\MEDIEVAL GLOOM (1)'
$packs = 'C:\Attachments\OneDrive\Desktop\additions\tarkov-pilot\packs'
$log = 'C:\Attachments\OneDrive\Desktop\additions\tarkov-pilot\b2copy.log'
'' | Set-Content $log
foreach ($p in 'a','b','c','d','e') {
  $z = Join-Path $packs "b2copy_$p.zip"
  try { Expand-Archive -LiteralPath $z -DestinationPath $prof -Force -ErrorAction Stop; "ok $p" | Add-Content $log }
  catch { "FAILED $p : $($_.Exception.Message)" | Add-Content $log }
}
$n = Join-Path $prof 'config\zomboidcraft\npc'
"tkp zsks: " + (Get-ChildItem $n -Filter 'tkp_*.zsk').Count | Add-Content $log
"tarkov guns: " + ((Get-ChildItem (Join-Path $prof 'config\zomboidcraft\guns') -Filter 'tarkov_*.json') | ForEach-Object Name) -join ' ' | Add-Content $log
'DONE' | Add-Content $log
Get-Content $log
