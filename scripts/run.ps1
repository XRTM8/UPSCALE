$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
. (Join-Path $PSScriptRoot 'env.ps1')
$applicationPath = @('target/release/upscale-studio.exe', 'target/debug/upscale-studio.exe') |
    ForEach-Object { Join-Path $projectRoot $_ } |
    Where-Object { Test-Path -LiteralPath $_ } |
    Select-Object -First 1
if (-not $applicationPath) { throw 'Build the application first with .\scripts\build.ps1.' }
& $applicationPath
