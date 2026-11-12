# Project-local development tools. No administrator access or persistent PATH changes.
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
New-Item -ItemType Directory -Path '.tools\downloads' -Force | Out-Null
$env:CARGO_HOME = Join-Path $projectRoot '.tools\cargo'
$env:RUSTUP_HOME = Join-Path $projectRoot '.tools\rustup'
if (-not (Test-Path -LiteralPath (Join-Path $env:CARGO_HOME 'bin\cargo.exe'))) {
    $installer = Join-Path $projectRoot '.tools\rustup-init.exe'
    Invoke-WebRequest -Uri 'https://static.rust-lang.org/rustup/dist/x86_64-pc-windows-gnu/rustup-init.exe' -OutFile $installer
    $digest = (Invoke-WebRequest -Uri 'https://static.rust-lang.org/rustup/dist/x86_64-pc-windows-gnu/rustup-init.exe.sha256').Content.Trim().Split(' ')[0]
    if ((Get-FileHash -LiteralPath $installer -Algorithm SHA256).Hash -ine $digest) { throw 'Rust installer checksum mismatch.' }
    & $installer -y --no-modify-path --profile minimal --default-host x86_64-pc-windows-gnu --default-toolchain stable --component rustfmt --component clippy --target wasm32-unknown-unknown
    if ($LASTEXITCODE -ne 0) { throw 'Rust installation failed.' }
}
$gcc = Join-Path $projectRoot '.tools\mingw\mingw64\bin\gcc.exe'
if (-not (Test-Path -LiteralPath $gcc)) {
    $archive = Join-Path $projectRoot '.tools\downloads\mingw.zip'
    $digest = '62af32ea58acdea1318ea5dd8eb7017eaee694dd40b269d52a59c769dcfefb82'
    if (-not (Test-Path -LiteralPath $archive) -or (Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $digest) {
        Invoke-WebRequest -Uri 'https://github.com/brechtsanders/winlibs_mingw/releases/download/16.2.0posix-23.1.3-14.0.0-msvcrt-r3/winlibs-x86_64-posix-seh-gcc-16.2.0-mingw-w64msvcrt-14.0.0-r3.zip' -OutFile $archive
    }
    if ((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash -ine $digest) { throw 'Compiler checksum mismatch.' }
    Expand-Archive -LiteralPath $archive -DestinationPath (Join-Path $projectRoot '.tools\mingw') -Force
}
. (Join-Path $PSScriptRoot 'install-trunk.ps1')
. (Join-Path $PSScriptRoot 'install-media-tools.ps1')
. (Join-Path $PSScriptRoot 'env.ps1')
pnpm install --frozen-lockfile
if ($LASTEXITCODE -ne 0) { throw 'Node dependencies failed to install.' }
pnpm assets
if ($LASTEXITCODE -ne 0) { throw 'Local assets could not be prepared.' }
Write-Output 'Ready. Run .\scripts\dev.ps1'
