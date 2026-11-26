param(
    [string] $ApkDirectory = 'app/build/outputs/apk/release',
    [string] $AndroidSdk = $env:ANDROID_HOME,
    [string] $OutputDirectory = 'release'
)

$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $projectRoot
$apkDir = (Resolve-Path -LiteralPath $ApkDirectory).Path
$output = [IO.Path]::GetFullPath($OutputDirectory)

$gradleKts = Get-Content -Raw -LiteralPath 'app/build.gradle.kts'
$versionMatch = [regex]::Match($gradleKts, 'versionName\s*=\s*"([^"]+)"')
$codeMatch = [regex]::Match($gradleKts, 'versionCode\s*=\s*(\d+)')
if (!$versionMatch.Success -or !$codeMatch.Success) {
    throw 'Could not parse versionName or versionCode from app/build.gradle.kts.'
}
$version = $versionMatch.Groups[1].Value
$versionCode = [long]$codeMatch.Groups[1].Value
$package = 'com.xrtm8.upscale'
$certificate = '39b2da1a2249a87b340b0c94b1d9098829f6f263bb9636f9743cb2c73aa56925'

if ($version -notmatch '^(\d+)\.(\d{1,3})\.(\d{1,3})$') {
    throw "Invalid versionName: $version"
}
$parts = $version.Split('.')
$expectedCode = [long]$parts[0] * 1000000 + [long]$parts[1] * 1000 + [long]$parts[2]
if ($versionCode -ne $expectedCode) {
    throw "versionCode ($versionCode) does not match versionName ($version -> $expectedCode)."
}

$buildRoot = Join-Path $env:LOCALAPPDATA 'UPSCALE\BuildTools'
if (!$AndroidSdk -or !(Test-Path -LiteralPath (Join-Path $AndroidSdk 'build-tools\37.0.0'))) {
    $AndroidSdk = Join-Path $buildRoot 'AndroidSDK'
}
$buildTools = Join-Path $AndroidSdk 'build-tools\37.0.0'
$apksigner = Join-Path $buildTools 'apksigner.bat'
$zipalign = Join-Path $buildTools 'zipalign.exe'
$aapt = Join-Path $buildTools 'aapt.exe'
$signing = Join-Path $env:LOCALAPPDATA 'UPSCALE\Signing'
$androidKey = Join-Path $signing 'android-release.p12'
foreach ($required in @($apksigner, $zipalign, $aapt, $androidKey)) {
    if (!(Test-Path -LiteralPath $required -PathType Leaf)) {
        throw "Required signing tool or key missing: $required"
    }
}

function Unprotect-Password([string] $name) {
    $encrypted = (Get-Content -Raw -LiteralPath (Join-Path $signing $name)).Trim()
    $secure = ConvertTo-SecureString $encrypted
    $bstr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
    try {
        [Runtime.InteropServices.Marshal]::PtrToStringAuto($bstr)
    } finally {
        [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($bstr)
    }
}

$releaseDir = Join-Path $output "v$version"
New-Item -ItemType Directory -Path $releaseDir -Force | Out-Null

$variants = @(
    @{ Name = 'arm64-v8a'; Input = 'app-arm64-v8a-release-unsigned.apk'; ExpectedAbis = @('arm64-v8a') },
    @{ Name = 'universal'; Input = 'app-universal-release-unsigned.apk'; ExpectedAbis = @('arm64-v8a', 'armeabi-v7a', 'x86', 'x86_64') }
)

$signedFiles = @()
try {
    $env:UPSCALE_ANDROID_SIGNING_PASSWORD = Unprotect-Password 'android-release.dpapi'
    foreach ($variant in $variants) {
        $unsignedPath = Join-Path $apkDir $variant.Input
        if (!(Test-Path -LiteralPath $unsignedPath -PathType Leaf)) {
            throw "Missing unsigned APK: $unsignedPath"
        }
        $aligned = Join-Path $releaseDir "$($variant.Name)-aligned.tmp.apk"
        $signedApk = Join-Path $releaseDir "UPSCALE-Studio-$version-android-$($variant.Name).apk"

        & $zipalign -P 16 -f 4 $unsignedPath $aligned
        if ($LASTEXITCODE -ne 0) { throw "APK zipalign failed for $($variant.Name)." }

        & $apksigner sign `
            --ks $androidKey `
            --ks-key-alias upscale `
            --ks-pass env:UPSCALE_ANDROID_SIGNING_PASSWORD `
            --key-pass env:UPSCALE_ANDROID_SIGNING_PASSWORD `
            --v4-signing-enabled false `
            --out $signedApk `
            $aligned
        if ($LASTEXITCODE -ne 0) { throw "APK signing failed for $($variant.Name)." }
        Remove-Item -LiteralPath $aligned

        & $zipalign -c -P 16 4 $signedApk
        if ($LASTEXITCODE -ne 0) { throw "APK alignment check failed for $($variant.Name)." }

        $verified = & $apksigner verify --min-sdk-version 29 --verbose --print-certs $signedApk
        $reportedCertificates = @(
            $verified | Select-String -Pattern 'certificate SHA-256 digest: ([a-fA-F0-9]{64})$' |
                ForEach-Object { $_.Matches[0].Groups[1].Value.ToLowerInvariant() } |
                Sort-Object -Unique
        )
        if ($LASTEXITCODE -ne 0 -or $reportedCertificates.Count -ne 1 -or $reportedCertificates[0] -cne $certificate) {
            throw "APK certificate verification failed for $($variant.Name)."
        }
        if (!($verified -match '^Verified using v[23] scheme .*: true$') -or !($verified -match '^Number of signers: 1$')) {
            throw "Invalid APK signing scheme for $($variant.Name)."
        }

        $badging = & $aapt dump badging $signedApk
        if ($LASTEXITCODE -ne 0 -or !($badging -match "^package: name='$([regex]::Escape($package))' versionCode='$versionCode' versionName='$([regex]::Escape($version))'")) {
            throw "APK identity or version mismatch for $($variant.Name)."
        }

        $actualAbis = @(
            tar -tf $signedApk |
                Where-Object { $_ -match '^lib/([^/]+)/[^/]+\.so$' } |
                ForEach-Object { ($_ -split '/')[1] } |
                Sort-Object -Unique
        )
        if ($LASTEXITCODE -ne 0) { throw "Failed to inspect ABIs in $($variant.Name)." }
        if (Compare-Object $variant.ExpectedAbis $actualAbis) {
            throw "ABI mismatch for $($variant.Name). Expected: $($variant.ExpectedAbis -join ', '); Actual: $($actualAbis -join ', ')"
        }

        Write-Output "Verified $($variant.Name) APK: $signedApk ($package v$version / $versionCode)"
        $signedFiles += $signedApk
    }
} finally {
    Remove-Item Env:UPSCALE_ANDROID_SIGNING_PASSWORD -ErrorAction SilentlyContinue
}
