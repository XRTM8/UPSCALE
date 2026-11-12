# Dot-source this script to prefer the project-local tools, when present.
$projectRoot = Split-Path -Parent $PSScriptRoot
$portableCargo = Join-Path $projectRoot '.tools\cargo'
if (Test-Path -LiteralPath (Join-Path $portableCargo 'bin\cargo.exe')) {
    $env:CARGO_HOME = $portableCargo
    $env:RUSTUP_HOME = Join-Path $projectRoot '.tools\rustup'
    $env:PATH = (Join-Path $portableCargo 'bin') + ';' + $env:PATH
}
$portableTools = Join-Path $projectRoot '.tools\bin'
if (Test-Path -LiteralPath $portableTools) { $env:PATH = $portableTools + ';' + $env:PATH }
$llvmRoot = Join-Path $projectRoot '.tools\llvm'
if (Test-Path -LiteralPath $llvmRoot) {
    $llvmBin = Get-ChildItem -LiteralPath $llvmRoot -Directory | ForEach-Object { Join-Path $_.FullName 'bin' } | Where-Object { Test-Path -LiteralPath $_ } | Select-Object -First 1
    if ($llvmBin) {
        $env:PATH = $llvmBin + ';' + $env:PATH
        $env:CARGO_TARGET_X86_64_PC_WINDOWS_GNU_LINKER = Join-Path $llvmBin 'x86_64-w64-mingw32-clang.exe'
        $env:CARGO_TARGET_X86_64_PC_WINDOWS_GNU_RUSTFLAGS = '-C link-self-contained=yes'
        $env:CC_x86_64_pc_windows_gnu = Join-Path $llvmBin 'x86_64-w64-mingw32-clang.exe'
        $env:AR_x86_64_pc_windows_gnu = Join-Path $llvmBin 'llvm-ar.exe'
    }
}
$mediaRoot = Join-Path $projectRoot '.tools\ffmpeg'
$mingwBin = Join-Path $projectRoot '.tools\mingw\mingw64\bin'
if (Test-Path -LiteralPath $mingwBin) {
    $env:PATH = $mingwBin + ';' + $env:PATH
    $env:CARGO_TARGET_X86_64_PC_WINDOWS_GNU_LINKER = Join-Path $mingwBin 'gcc.exe'
    $env:CC_x86_64_pc_windows_gnu = Join-Path $mingwBin 'gcc.exe'
    $env:AR_x86_64_pc_windows_gnu = Join-Path $mingwBin 'ar.exe'
}
if (Test-Path -LiteralPath $mediaRoot) {
    $mediaTool = Get-ChildItem -LiteralPath $mediaRoot -Recurse -Filter 'ffmpeg.exe' | Select-Object -First 1
    if ($mediaTool) { $env:PATH = $mediaTool.DirectoryName + ';' + $env:PATH }
}
