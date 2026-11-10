$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = Split-Path -Parent $PSScriptRoot
$downloads = Join-Path $projectRoot '.tools\downloads'
$destination = Join-Path $projectRoot '.tools\ffmpeg'
New-Item -ItemType Directory -Path $downloads -Force | Out-Null
$archive = Join-Path $downloads 'ffmpeg.zip'
$expected = '60f467265b1e312373dbcd92200c2618a74850f98d3d078e94296bb3fa2047ba'
if (-not (Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $expected) {
    Invoke-WebRequest -Uri 'https://www.gyan.dev/ffmpeg/builds/packages/ffmpeg-9.0.2-essentials_build.zip' -OutFile $archive
}
if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $expected) { throw 'FFmpeg checksum mismatch.' }
if (-not (Test-Path -LiteralPath (Join-Path $destination 'ffmpeg-9.0.2-essentials_build\bin\ffmpeg.exe'))) {
    Expand-Archive -LiteralPath $archive -DestinationPath $destination -Force
}
$mediaBin = Join-Path $destination 'ffmpeg-9.0.2-essentials_build\bin'
$env:PATH = $mediaBin + ';' + $env:PATH
Write-Output "FFmpeg 9.0.2 is available in $mediaBin"
