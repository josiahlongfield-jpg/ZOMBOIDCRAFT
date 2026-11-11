# Packs the batch 3 bundles (batch3-list.txt, already copied into raw\) into store-only zips packs\b3_<n>.zip of at most ~23 MB each
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$raw = Join-Path $here 'raw'
$out = Join-Path $here 'packs'
New-Item -ItemType Directory -Force -Path $out | Out-Null
$limit = 23MB
$n = 0; $size = 0; $z = $null; $lines = @()
foreach ($rel in Get-Content (Join-Path $here 'batch3-list.txt')) {
  if (-not $rel.Trim()) { continue }
  $f = 'EscapeFromTarkov_Data__StreamingAssets__Windows__' + ($rel -replace '/', '__')
  $p = Join-Path $raw $f
  if (-not (Test-Path -LiteralPath $p)) { continue }
  $len = (Get-Item -LiteralPath $p).Length
  if ($z -eq $null -or ($size + $len -gt $limit -and $size -gt 0)) {
    if ($z -ne $null) { $z.Dispose() }
    $n++; $size = 0
    $zip = Join-Path $out ("b3_$n.zip")
    if (Test-Path $zip) { Remove-Item $zip }
    $z = [System.IO.Compression.ZipFile]::Open($zip, 'Create')
  }
  [System.IO.Compression.ZipFileExtensions]::CreateEntryFromFile($z, $p, $f, [System.IO.Compression.CompressionLevel]::NoCompression) | Out-Null
  $size += $len
  $lines += "$n`t$f`t$len"
}
if ($z -ne $null) { $z.Dispose() }
$lines | Set-Content (Join-Path $here 'batch3-packs.txt') -Encoding utf8
Write-Host "packs: $n"
