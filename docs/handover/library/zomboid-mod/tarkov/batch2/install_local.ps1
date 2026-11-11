# Unpacks every tlp_*.zip next to this script (config\zomboidcraft\{guns,npc,hands} and resourcepacks\ZomboidCraft Tarkov Sounds) into the MEDIEVAL GLOOM (1) Modrinth profile.
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$profile = Join-Path $env:APPDATA 'ModrinthApp\profiles\MEDIEVAL GLOOM (1)'
if (-not (Test-Path -LiteralPath $profile)) { throw "profile not found: $profile" }
foreach ($z in Get-ChildItem -LiteralPath (Join-Path $here 'lpc') -Filter 'tlp_*.zip') { Expand-Archive -LiteralPath $z.FullName -DestinationPath $profile -Force }
$n = (Get-ChildItem -LiteralPath (Join-Path $profile 'config\zomboidcraft\npc') -Filter 'tkp_*.zsk').Count
Write-Host "installed; tkp parts: $n"
