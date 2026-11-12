param(
    [Parameter(Mandatory)][string] $BackupPath,
    [switch] $Restore
)

$ErrorActionPreference = 'Stop'
$signingDirectory = Join-Path $env:LOCALAPPDATA 'UPSCALE\Signing'
$backupFile = [IO.Path]::GetFullPath($BackupPath)
$identity = 'com.xrtm8.upscale'
$context = [Text.Encoding]::UTF8.GetBytes($identity)
$passphrase = Read-Host 'Backup passphrase' -AsSecureString
if ($passphrase.Length -lt 16) { throw 'Use a passphrase with at least 16 characters.' }
$plainPassphrase = ConvertFrom-SecureString $passphrase -AsPlainText

function Unprotect-SigningPassword([string] $name) {
    $protected = (Get-Content -Raw -LiteralPath (Join-Path $signingDirectory $name)).Trim()
    ConvertFrom-SecureString (ConvertTo-SecureString $protected) -AsPlainText
}

if ($Restore) {
    if (Test-Path -LiteralPath $signingDirectory) { throw 'Restore requires a new signing directory.' }
    $backup = Get-Content -Raw -LiteralPath $backupFile | ConvertFrom-Json
    if ($backup.format -ne 1 -or $backup.application -cne $identity) { throw 'Invalid UPSCALE signing backup.' }
    $salt = [Convert]::FromBase64String($backup.salt)
    $nonce = [Convert]::FromBase64String($backup.nonce)
    $tag = [Convert]::FromBase64String($backup.tag)
    $ciphertext = [Convert]::FromBase64String($backup.payload)
    $plaintext = [byte[]]::new($ciphertext.Length)
} else {
    $salt = [Security.Cryptography.RandomNumberGenerator]::GetBytes(16)
    $nonce = [Security.Cryptography.RandomNumberGenerator]::GetBytes(12)
    $tag = [byte[]]::new(16)
    $data = @{
        windows = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $signingDirectory 'windows-updater.key')))
        windowsPublic = (Get-Content -Raw -LiteralPath (Join-Path $signingDirectory 'windows-updater.key.pub')).Trim()
        windowsPassword = Unprotect-SigningPassword 'windows-updater.dpapi'
        android = [Convert]::ToBase64String([IO.File]::ReadAllBytes((Join-Path $signingDirectory 'android-release.p12')))
        androidPassword = Unprotect-SigningPassword 'android-release.dpapi'
    }
    $plaintext = [Text.Encoding]::UTF8.GetBytes(($data | ConvertTo-Json -Compress))
    $ciphertext = [byte[]]::new($plaintext.Length)
}

$key = [Security.Cryptography.Rfc2898DeriveBytes]::Pbkdf2($plainPassphrase, $salt, 600000, [Security.Cryptography.HashAlgorithmName]::SHA256, 32)
$aes = [Security.Cryptography.AesGcm]::new($key, 16)
try {
    if ($Restore) {
        $aes.Decrypt($nonce, $ciphertext, $tag, $plaintext, $context)
        $data = [Text.Encoding]::UTF8.GetString($plaintext) | ConvertFrom-Json
        $config = Get-Content -Raw -LiteralPath (Join-Path (Split-Path -Parent $PSScriptRoot) 'src-tauri/tauri.windows.conf.json') | ConvertFrom-Json
        if ($data.windowsPublic -cne $config.plugins.updater.pubkey) { throw 'The backup belongs to a different release identity.' }
        $windowsKey = [Convert]::FromBase64String($data.windows)
        $androidKey = [Convert]::FromBase64String($data.android)
        New-Item -ItemType Directory -Path $signingDirectory | Out-Null
        [IO.File]::WriteAllBytes((Join-Path $signingDirectory 'windows-updater.key'), $windowsKey)
        [IO.File]::WriteAllBytes((Join-Path $signingDirectory 'android-release.p12'), $androidKey)
        Set-Content -LiteralPath (Join-Path $signingDirectory 'windows-updater.key.pub') -Value $data.windowsPublic
        ConvertFrom-SecureString (ConvertTo-SecureString $data.windowsPassword -AsPlainText -Force) |
            Set-Content -LiteralPath (Join-Path $signingDirectory 'windows-updater.dpapi')
        ConvertFrom-SecureString (ConvertTo-SecureString $data.androidPassword -AsPlainText -Force) |
            Set-Content -LiteralPath (Join-Path $signingDirectory 'android-release.dpapi')
        Write-Output "Restored the signing files to $signingDirectory."
    } else {
        $aes.Encrypt($nonce, $plaintext, $ciphertext, $tag, $context)
        $json = @{
            format = 1
            application = $identity
            salt = [Convert]::ToBase64String($salt)
            nonce = [Convert]::ToBase64String($nonce)
            tag = [Convert]::ToBase64String($tag)
            payload = [Convert]::ToBase64String($ciphertext)
        } | ConvertTo-Json -Compress
        $stream = [IO.File]::Open($backupFile, [IO.FileMode]::CreateNew)
        try {
            $bytes = [Text.Encoding]::UTF8.GetBytes($json)
            $stream.Write($bytes, 0, $bytes.Length)
        } finally {
            $stream.Dispose()
        }
        Write-Output "Saved the encrypted signing backup to $backupFile."
    }
} finally {
    $aes.Dispose()
    [Array]::Clear($key)
    [Array]::Clear($plaintext)
    $passphrase.Dispose()
}
