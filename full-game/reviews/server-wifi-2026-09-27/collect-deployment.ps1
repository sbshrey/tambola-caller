$ErrorActionPreference='Stop'
$hostRoot=Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost'
$config=Get-Content -LiteralPath (Join-Path $hostRoot 'host.json') -Raw | ConvertFrom-Json
$upgradeRoot=[IO.Path]::GetFullPath((Split-Path $config.serviceLib -Parent))
if (!$upgradeRoot.StartsWith([IO.Path]::GetFullPath((Join-Path $hostRoot 'upgrades'))+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected upgrade location' }
$deployment=Get-Content -LiteralPath (Join-Path $upgradeRoot 'deployment.json') -Raw | ConvertFrom-Json
if ($deployment.sourceCommit -ne 'b6d5eb6f7cc9c6c70006e4d9c422c2abe75187e0' -or $config.sourceCommit -ne $deployment.sourceCommit) { throw 'Unexpected deployment identity' }
$backupRoot=Join-Path $upgradeRoot 'backups'
$manifest=Get-Content -LiteralPath (Join-Path $backupRoot $deployment.backupPair) -Raw | ConvertFrom-Json
if ($manifest.files.Count -ne 2) { throw 'Incomplete retained backup pair' }
foreach($entry in $manifest.files) {
    if($entry.name -notmatch '^\d{8}-\d{6}-(primary|journal)\.dump$') { throw 'Invalid retained backup name' }
    $archive=Join-Path $backupRoot $entry.name
    if((Get-FileHash -LiteralPath $archive -Algorithm SHA256).Hash.ToLowerInvariant() -ne $entry.sha256 -or (Get-Item -LiteralPath $archive).Length -ne $entry.bytes) { throw 'Retained backup bytes changed' }
    & (Join-Path $config.postgresBin 'pg_restore.exe') --list $archive *> $null
    if($LASTEXITCODE -ne 0) { throw 'Retained archive listing failed' }
}
$protected=(Get-Content -LiteralPath (Join-Path $hostRoot 'runtime.secrets') -Raw).Trim() | ConvertTo-SecureString
$pointer=[Runtime.InteropServices.Marshal]::SecureStringToBSTR($protected)
try { $runtime=[Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) | ConvertFrom-Json -AsHashtable }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $protected.Dispose() }
$versions=@{}
try {
    foreach($kind in @('primary','journal')) {
        $prefix=if($kind -eq 'primary') {'TAMBOLA_DATABASE'} else {'TAMBOLA_DELETION_DATABASE'}
        $name=if($kind -eq 'primary') {'tambola_local'} else {'tambola_local_journal'}
        if($runtime["$($prefix)_URL"] -ne "jdbc:postgresql://127.0.0.1:55433/$name") { throw 'Unexpected installed database' }
        $env:PGPASSWORD=$runtime["$($prefix)_PASSWORD"]
        $table=if($kind -eq 'primary') {'schema_migrations'} else {'journal_migrations'}
        $value=& (Join-Path $config.postgresBin 'psql.exe') -X --no-password -h 127.0.0.1 -p 55433 -U $runtime["$($prefix)_USER"] -d $name -At -v ON_ERROR_STOP=1 -c "SELECT max(version) FROM $table"
        if($LASTEXITCODE -ne 0) { throw 'Could not read installed migration version' }
        $versions[$kind]=[int]$value
    }
} finally { $runtime.Clear(); Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
if($versions.primary -ne 7 -or $versions.journal -ne 3) { throw 'Unexpected installed migration versions' }
@{checkedAt=[DateTimeOffset]::UtcNow.ToString('o');deployment=$deployment;schemaVersions=$versions;retainedBackupPair=$manifest;retainedArchivesHashAndListVerified=$true;backupRestoreRehearsed=$false;collectorSha256=(Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()} |
    ConvertTo-Json -Depth 8 | Set-Content -LiteralPath (Join-Path $PSScriptRoot 'server-wifi-deployment-state.json') -Encoding utf8
Write-Output 'Installed schemas 007/003 confirmed; both retained backup archives match their hashes and remain readable.'
