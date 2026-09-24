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
    $metadata = Get-Content -LiteralPath (Join-Path (Split-Path $apk) 'output-metadata.json') -Raw | ConvertFrom-Json
    $version = $metadata.elements[0].versionName
    if ($version -notmatch '^\d+\.\d+\.\d+$') { throw 'Unexpected APK version.' }
    $releaseDirectory = Join-Path $PSScriptRoot "releases\$version"
    New-Item -ItemType Directory -Force -Path $releaseDirectory | Out-Null
    $releaseApk = Join-Path $releaseDirectory "Tambola-Keyboard-$version.apk"
    Copy-Item -LiteralPath $apk -Destination $releaseApk
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'INSTALL.txt') -Destination (Join-Path $releaseDirectory 'INSTALL.txt')
    $hash = (Get-FileHash -LiteralPath $releaseApk -Algorithm SHA256).Hash.ToLowerInvariant()
    Set-Content -LiteralPath (Join-Path $releaseDirectory 'SHA256SUMS.txt') -Encoding ascii -Value "$hash  Tambola-Keyboard-$version.apk"
    Write-Output "Signed APK: $releaseApk"
    Write-Output "SHA256: $hash"
} finally {
    $env:TAMBOLA_KEYSTORE_PATH = $previousKey
    $env:TAMBOLA_KEYSTORE_PASSWORD = $previousPassword
}
