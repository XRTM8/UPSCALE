$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
. (Join-Path $PSScriptRoot 'env.ps1')
cargo fmt --all -- --check
if ($LASTEXITCODE -ne 0) { throw 'Formatting failed.' }
cargo clippy -p upscale-core -p upscale-studio --all-targets -- -D warnings
if ($LASTEXITCODE -ne 0) { throw 'Native checks failed.' }
cargo clippy -p upscale-ui --target wasm32-unknown-unknown -- -D warnings
if ($LASTEXITCODE -ne 0) { throw 'UI checks failed.' }
cargo test -p upscale-core
if ($LASTEXITCODE -ne 0) { throw 'Core tests failed.' }
trunk build
if ($LASTEXITCODE -ne 0) { throw 'Frontend build failed.' }
pnpm test:ui
if ($LASTEXITCODE -ne 0) { throw 'UI tests failed.' }
