$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'env.ps1')
pnpm tauri dev
exit $LASTEXITCODE
