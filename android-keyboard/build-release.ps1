param([string]$SigningDirectory = (Join-Path $env:LOCALAPPDATA 'TambolaKeyboard\signing'))
$ErrorActionPreference = 'Stop'
$keyPath = Join-Path $SigningDirectory 'tambola-release.jks'
$passwordPath = Join-Path $SigningDirectory 'password.dpapi'
$previousKey = $env:TAMBOLA_KEYSTORE_PATH
$previousPassword = $env:TAMBOLA_KEYSTORE_PASSWORD
try {
    if (!(Test-Path -LiteralPath $keyPath)) {
        if (Test-Path -LiteralPath $passwordPath) { throw 'Signing key is missing. Restore the existing key; do not replace it.' }
        New-Item -ItemType Directory -Force -Path $SigningDirectory | Out-Null
        $bytes = New-Object byte[] 32
        $random = [System.Security.Cryptography.RandomNumberGenerator]::Create()
        $random.GetBytes($bytes)
        $random.Dispose()
        $env:TAMBOLA_KEYSTORE_PASSWORD = [Convert]::ToBase64String($bytes)
        $securePassword = ConvertTo-SecureString $env:TAMBOLA_KEYSTORE_PASSWORD -AsPlainText -Force
        ConvertFrom-SecureString $securePassword | Set-Content -LiteralPath $passwordPath
        & keytool -genkeypair -keystore $keyPath -alias tambola -keyalg RSA -keysize 3072 -validity 10950 -dname 'CN=Tambola Keyboard' -storepass:env TAMBOLA_KEYSTORE_PASSWORD -keypass:env TAMBOLA_KEYSTORE_PASSWORD
        if ($LASTEXITCODE -ne 0) { throw 'Signing key creation failed.' }
    } else {
        $securePassword = ConvertTo-SecureString ((Get-Content -LiteralPath $passwordPath -Raw).Trim())
        $env:TAMBOLA_KEYSTORE_PASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password
    }
    $env:TAMBOLA_KEYSTORE_PATH = $keyPath
    & "$PSScriptRoot\gradlew.bat" -p $PSScriptRoot testDebugUnitTest assembleRelease lintRelease --console=plain
    if ($LASTEXITCODE -ne 0) { throw 'Release checks failed.' }
    $apk = Join-Path $PSScriptRoot 'app\build\outputs\apk\release\app-release.apk'
    if (!(Test-Path -LiteralPath $apk)) { throw 'Signed APK not found.' }
    Write-Output "Signed APK: $apk"
    Get-FileHash -LiteralPath $apk -Algorithm SHA256
} finally {
    $env:TAMBOLA_KEYSTORE_PATH = $previousKey
    $env:TAMBOLA_KEYSTORE_PASSWORD = $previousPassword
}
