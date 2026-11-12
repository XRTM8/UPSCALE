param(
    [Parameter(Mandatory)][string] $WindowsArtifacts,
    [Parameter(Mandatory)][string] $AndroidArtifacts,
    [string] $AndroidSdk = $env:ANDROID_HOME,
    [string] $OutputDirectory = 'release'
)

$ErrorActionPreference = 'Stop'
$windowsInput = (Resolve-Path -LiteralPath $WindowsArtifacts).Path
$androidInput = (Resolve-Path -LiteralPath $AndroidArtifacts).Path
$output = [IO.Path]::GetFullPath($OutputDirectory)
Set-Location -LiteralPath (Split-Path -Parent $PSScriptRoot)
$config = Get-Content -Raw -LiteralPath 'src-tauri/tauri.conf.json' | ConvertFrom-Json
$windows = Get-Content -Raw -LiteralPath 'src-tauri/tauri.windows.conf.json' | ConvertFrom-Json
$version = $config.version
$package = 'com.xrtm8.upscale'
$certificate = '39b2da1a2249a87b340b0c94b1d9098829f6f263bb9636f9743cb2c73aa56925'
if ($config.identifier -cne $package -or $version -notmatch '^(\d+)\.(\d{1,3})\.(\d{1,3})$') {
    throw 'Release application ID or version is invalid.'
}
$parts = $version.Split('.')
$versionCode = [long]$parts[0] * 1000000 + [long]$parts[1] * 1000 + [long]$parts[2]
if ($versionCode -ne $config.bundle.android.versionCode -or $versionCode -gt 2100000000) {
    throw 'The Android version code must match the release version.'
}

$buildRoot = Join-Path $env:LOCALAPPDATA 'UPSCALE\BuildTools'
if (!$AndroidSdk) { $AndroidSdk = Join-Path $buildRoot 'AndroidSDK' }
if (!$env:JAVA_HOME -and (Test-Path -LiteralPath $buildRoot)) {
    $jdk = Get-ChildItem -LiteralPath $buildRoot -Directory -Filter 'jdk-17*' | Select-Object -First 1
    if ($jdk) { $env:JAVA_HOME = $jdk.FullName }
}
$buildTools = Join-Path $AndroidSdk 'build-tools\37.0.0'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$aapt = Join-Path $buildTools 'aapt.exe'
$signing = Join-Path $env:LOCALAPPDATA 'UPSCALE\Signing'
$updaterKey = Join-Path $signing 'windows-updater.key'
$androidKey = Join-Path $signing 'android-release.p12'
foreach ($required in @($apksigner, $zipalign, $aapt, $updaterKey, ($updaterKey + '.pub'), $androidKey)) {
    if (!(Test-Path -LiteralPath $required -PathType Leaf)) { throw "Required signing file missing: $required" }
}
if ((Get-Content -Raw -LiteralPath ($updaterKey + '.pub')).Trim() -cne $windows.plugins.updater.pubkey) {
    throw 'The Windows updater key differs from the release identity.'
}

function Unprotect-Password([string] $name) {
    $encrypted = (Get-Content -Raw -LiteralPath (Join-Path $signing $name)).Trim()
    ConvertFrom-SecureString (ConvertTo-SecureString $encrypted) -AsPlainText
}

New-Item -ItemType Directory -Path $output -Force | Out-Null
$prefix = "UPSCALE-Studio-$version"
$portable = Join-Path $output "$prefix-windows-x64-portable.exe"
$installer = Join-Path $output "$prefix-windows-x64-setup.exe"
Copy-Item -LiteralPath (Join-Path $windowsInput 'portable.exe') -Destination $portable
Copy-Item -LiteralPath (Join-Path $windowsInput 'setup.exe') -Destination $installer

try {
    $env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD = Unprotect-Password 'windows-updater.dpapi'
    node node_modules/@tauri-apps/cli/tauri.js signer sign --private-key-path $updaterKey --app-version $version $installer | Out-Null
    if ($LASTEXITCODE -ne 0) { throw 'Windows updater signing failed.' }
} finally {
    Remove-Item Env:TAURI_SIGNING_PRIVATE_KEY_PASSWORD -ErrorAction SilentlyContinue
}
$signaturePath = "$installer.sig"
node scripts/verify-update.mjs $installer $signaturePath $version
if ($LASTEXITCODE -ne 0) { throw 'Windows updater signature verification failed.' }

$apks = @()
try {
    $env:UPSCALE_ANDROID_SIGNING_PASSWORD = Unprotect-Password 'android-release.dpapi'
    foreach ($variant in @('arm64-v8a', 'universal')) {
        $unsignedApk = Join-Path $androidInput "$variant-unsigned.apk"
        $aligned = Join-Path $output "$variant-aligned.tmp.apk"
        $apk = Join-Path $output "$prefix-android-$variant.apk"
        & $zipalign -P 16 -f 4 $unsignedApk $aligned
        if ($LASTEXITCODE -ne 0) { throw "APK alignment failed for $variant." }
        & $apksigner sign --ks $androidKey --ks-key-alias upscale --ks-pass env:UPSCALE_ANDROID_SIGNING_PASSWORD --key-pass env:UPSCALE_ANDROID_SIGNING_PASSWORD --v4-signing-enabled false --out $apk $aligned
        if ($LASTEXITCODE -ne 0) { throw "APK signing failed for $variant." }
        Remove-Item -LiteralPath $aligned
        & $zipalign -c -P 16 4 $apk
        if ($LASTEXITCODE -ne 0) { throw "APK alignment check failed for $variant." }

        $verified = & $apksigner verify --min-sdk-version $config.bundle.android.minSdkVersion --verbose --print-certs $apk
        $reportedCertificates = @($verified | Select-String -Pattern 'certificate SHA-256 digest: ([a-fA-F0-9]{64})$' |
            ForEach-Object { $_.Matches[0].Groups[1].Value.ToLowerInvariant() } | Sort-Object -Unique)
        if ($LASTEXITCODE -ne 0 -or $reportedCertificates.Count -ne 1 -or $reportedCertificates[0] -cne $certificate) {
            throw "APK certificate verification failed for $variant."
        }
        if (!($verified -match '^Verified using v[23] scheme .*: true$') -or !($verified -match '^Number of signers: 1$')) {
            throw "Invalid APK signing scheme for $variant."
        }
        $badging = & $aapt dump badging $apk
        if ($LASTEXITCODE -ne 0 -or !($badging -match "^package: name='$([regex]::Escape($package))' versionCode='$versionCode' versionName='$([regex]::Escape($version))'")) {
            throw "APK identity or version mismatch for $variant."
        }
        $abi = @(tar -tf $apk | Where-Object { $_ -match '^lib/([^/]+)/libupscale_studio\.so$' } | ForEach-Object { ($_ -split '/')[1] } | Sort-Object -Unique)
        if ($LASTEXITCODE -ne 0) { throw "Cannot inspect libraries in $variant." }
        $expected = if ($variant -eq 'universal') { @('arm64-v8a','armeabi-v7a','x86','x86_64') } else { @('arm64-v8a') }
        if (Compare-Object $expected $abi) { throw "Unexpected native architectures in $variant." }
        Write-Output "Verified $variant APK, $package, version $version ($versionCode)."
        $apks += $apk
    }
} finally {
    Remove-Item Env:UPSCALE_ANDROID_SIGNING_PASSWORD -ErrorAction SilentlyContinue
}

$manifest = Join-Path $output 'latest.json'
@{
    version = $version
    android = @{ package = $package; versionCode = $versionCode; certificateSha256 = $certificate }
    platforms = @{
        'windows-x86_64' = @{
            url = "https://github.com/XRTM8/UPSCALE/releases/download/v$version/$([IO.Path]::GetFileName($installer))"
            signature = (Get-Content -Raw -LiteralPath $signaturePath).Trim()
        }
    }
} | ConvertTo-Json -Depth 5 | Set-Content -Encoding utf8NoBOM -LiteralPath $manifest

@($apks + $portable + $installer + $signaturePath + $manifest) | ForEach-Object {
    $hash = (Get-FileHash -LiteralPath $_ -Algorithm SHA256).Hash.ToLowerInvariant()
    "$hash  $([IO.Path]::GetFileName($_))"
} | Set-Content -Encoding utf8NoBOM -LiteralPath (Join-Path $output 'SHA256SUMS.txt')
