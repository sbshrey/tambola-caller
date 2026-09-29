#requires -Version 7.0
# Prepare an immutable OTA asset. This does not publish a release or change the server.
param(
    [Parameter(Mandatory)][string]$Apk,
    [Parameter(Mandatory)][string]$PreviousApk,
    [string]$BuildTools = "$env:LOCALAPPDATA/Android/Sdk/build-tools/36.0.0"
)
$ErrorActionPreference = 'Stop'
function Read-Apk([string]$Path) {
    $resolved = (Resolve-Path -LiteralPath $Path).Path
    $badging = (& "$BuildTools/aapt.exe" dump badging $resolved) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'Cannot read APK' }
    $identity = [regex]::Match($badging, "package: name='([^']+)' versionCode='([0-9]+)'")
    if (!$identity.Success -or $identity.Groups[1].Value -ne 'io.github.sbshrey.tambola.game.beta') { throw 'Expected an Internet Beta APK' }
    $signing = (& "$BuildTools/apksigner.bat" verify --print-certs $resolved) -join "`n"
    if ($LASTEXITCODE -ne 0) { throw 'APK signature validation failed' }
    $certs = @([regex]::Matches($signing, 'Signer #[0-9]+ certificate SHA-256 digest: ([a-f0-9]+)') | ForEach-Object { $_.Groups[1].Value })
    if ($certs.Count -ne 1) { throw 'Expected exactly one signing certificate' }
    return @{ path=$resolved; version=[int]$identity.Groups[2].Value; certificate=$certs[0]; sha256=(Get-FileHash -LiteralPath $resolved -Algorithm SHA256).Hash.ToLower() }
}
$candidate = Read-Apk $Apk
$previous = Read-Apk $PreviousApk
if ($candidate.version -le $previous.version) { throw 'Increment versionCode before preparing an update' }
if ($candidate.certificate -ne $previous.certificate) { throw 'Update signing certificate must match the previous APK' }
$destination = Join-Path $PSScriptRoot "../releases/ota-v$($candidate.version)"
New-Item -ItemType Directory -Path $destination -Force | Out-Null
$asset = Join-Path $destination "tambola-beta-v$($candidate.version).apk"
if (Test-Path -LiteralPath $asset) {
    if ((Get-FileHash -LiteralPath $asset -Algorithm SHA256).Hash.ToLower() -ne $candidate.sha256) { throw 'Version already prepared with different bytes; use a new versionCode' }
} else { Copy-Item -LiteralPath $candidate.path -Destination $asset }
@{versionCode=$candidate.version;packageName='io.github.sbshrey.tambola.game.beta';sha256=$candidate.sha256;bytes=(Get-Item -LiteralPath $asset).Length;certificateSha256=$candidate.certificate;asset=(Resolve-Path -LiteralPath $asset).Path} | ConvertTo-Json | Set-Content -Encoding utf8 (Join-Path $destination 'update.json')
Write-Output "Prepared: $((Resolve-Path -LiteralPath $asset).Path)"
Write-Output 'To distribute: attach this exact APK to a published GitHub release in sbshrey/tambola-caller. Never replace an existing version asset. The GitHub asset digest must match update.json.'
