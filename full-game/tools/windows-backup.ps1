#requires -Version 7.0
param([string]$Directory = (Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost'))
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath($Directory).TrimEnd('\')
$configuration = Get-Content -LiteralPath (Join-Path $root 'host.json') -Raw | ConvertFrom-Json
$backups = [IO.Path]::GetFullPath((Join-Path $root 'backups'))
if (!$backups.StartsWith($root + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid backup boundary' }
try { $lock = [IO.File]::Open((Join-Path $root 'backup.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
catch [IO.IOException] { exit }
$settings = @{}
try {
    $encrypted = (Get-Content -LiteralPath (Join-Path $root 'runtime.secrets') -Raw).Trim() | ConvertTo-SecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($encrypted)
    try { $settings = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) | ConvertFrom-Json -AsHashtable }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $encrypted.Dispose() }
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
    $entries = @()
    # Primary first, journal second: the journal snapshot is never older than the
    # primary dump. Recovery must also replay every deletion newer than either dump.
    foreach ($kind in @('primary','journal')) {
        $prefix = if ($kind -eq 'primary') {'TAMBOLA_DATABASE'} else {'TAMBOLA_DELETION_DATABASE'}
        $database = if ($kind -eq 'primary') {'tambola_local'} else {'tambola_local_journal'}
        if ($settings["$($prefix)_URL"] -ne "jdbc:postgresql://127.0.0.1:55433/$database") { throw 'Unexpected backup source' }
        $file = Join-Path $backups "$stamp-$kind.dump"
        $pending = "$file.pending"
        $env:PGPASSWORD = $settings["$($prefix)_PASSWORD"]
        try {
            & (Join-Path $configuration.postgresBin 'pg_dump.exe') --no-password -h 127.0.0.1 -p 55433 -U $settings["$($prefix)_USER"] -d $database --format=custom --no-owner --no-privileges --file=$pending *> (Join-Path $root 'logs/backup-latest.log')
            if ($LASTEXITCODE -ne 0) { throw 'Database backup failed' }
            & (Join-Path $configuration.postgresBin 'pg_restore.exe') --list $pending *> $null
            if ($LASTEXITCODE -ne 0 -or (Get-Item -LiteralPath $pending).Length -le 0) { throw 'Backup archive validation failed' }
            Move-Item -LiteralPath $pending -Destination $file
            $entries += @{name=[IO.Path]::GetFileName($file); sha256=(Get-FileHash -LiteralPath $file -Algorithm SHA256).Hash.ToLowerInvariant(); bytes=(Get-Item -LiteralPath $file).Length}
        } finally { Remove-Item Env:PGPASSWORD -ErrorAction SilentlyContinue }
    }
    @{completedAt=[DateTime]::UtcNow.ToString('o'); files=$entries; validation='pg_restore archive listing; restore rehearsal remains separate'} |
        ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $backups "$stamp.json")
    $old = Get-ChildItem -LiteralPath $backups -File -Filter '*.json' | Where-Object Name -Match '^\d{8}-\d{6}\.json$' | Sort-Object Name -Descending | Select-Object -Skip 7
    foreach ($manifest in $old) {
        $date = $manifest.BaseName
        foreach ($name in @("$date-primary.dump", "$date-journal.dump", "$date.json")) {
            $target = [IO.Path]::GetFullPath((Join-Path $backups $name))
            if (!$target.StartsWith($backups + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Backup retention path escaped its boundary' }
            if (Test-Path -LiteralPath $target) { Remove-Item -LiteralPath $target }
        }
    }
    Write-Output 'Both database archives validated; latest seven complete pairs retained.'
} finally { $settings.Clear(); $lock.Dispose() }
