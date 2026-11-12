$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = Split-Path -Parent $PSScriptRoot
$bin = Join-Path $projectRoot '.tools\bin'
$downloads = Join-Path $projectRoot '.tools\downloads'
New-Item -ItemType Directory -Path $bin,$downloads -Force | Out-Null
if (-not (Test-Path -LiteralPath (Join-Path $bin 'trunk.exe'))) {
    $release = Invoke-RestMethod -Uri 'https://api.github.com/repos/trunk-rs/trunk/releases/tags/v0.22.1'
    $asset = $release.assets | Where-Object { $_.name -eq 'trunk-x86_64-pc-windows-msvc.zip' } | Select-Object -First 1
    if (-not $asset -or $asset.digest -notlike 'sha256:*') { throw 'A verified Trunk release could not be found.' }
    $archive = Join-Path $downloads 'trunk.zip'
    Invoke-WebRequest -Uri $asset.browser_download_url -OutFile $archive
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $asset.digest.Substring(7)) { throw 'Trunk checksum mismatch.' }
    Expand-Archive -LiteralPath $archive -DestinationPath $bin -Force
}
$env:PATH = $bin + ';' + $env:PATH
& (Join-Path $bin 'trunk.exe') --version
if ($LASTEXITCODE -ne 0) { throw 'Trunk could not run.' }
