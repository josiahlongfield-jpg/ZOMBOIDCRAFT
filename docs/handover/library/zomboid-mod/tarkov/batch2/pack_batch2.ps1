# Packs the batch 2 bundles listed in batch2-packs.txt (pack number <tab> file in raw) into store-only zips packs\b2_<n>.zip
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$raw = Join-Path $here 'raw'
$out = Join-Path $here 'packs'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$groups = @{}
foreach ($l in Get-Content (Join-Path $here 'batch2-packs.txt')) {
  if (-not $l.Trim()) { continue }
  $p = $l.Split("`t"); if (-not $groups.ContainsKey($p[0])) { $groups[$p[0]] = @() }; $groups[$p[0]] += $p[1]
}
foreach ($k in $groups.Keys) {
  $zip = Join-Path $out ("b2_$k.zip")
  if (Test-Path $zip) { Remove-Item $zip }
  $z = [System.IO.Compression.ZipFile]::Open($zip, 'Create')
  foreach ($f in $groups[$k]) { [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z, (Join-Path $raw $f), $f, [System.IO.Compression.CompressionLevel]::NoCompression) | Out-Null }
  $z.Dispose()
  Write-Host "$zip $((Get-Item $zip).Length)"
}
