$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'env.ps1')
pnpm assets
if ($LASTEXITCODE -ne 0) { throw 'Asset build failed.' }
pnpm tauri build --no-bundle
exit $LASTEXITCODE
