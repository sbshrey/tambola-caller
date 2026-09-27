#requires -Version 7.0
[CmdletBinding()]
param(
    [Parameter(Mandatory)][ValidatePattern('^[0-9a-f]{40}$')][string]$SourceCommit,
    [switch]$Apply,
    [string]$Directory = (Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost')
)
$ErrorActionPreference = 'Stop'
$hostRoot = [IO.Path]::GetFullPath($Directory).TrimEnd('\')
$gameRoot = Split-Path -Parent $PSScriptRoot
$configPath = Join-Path $hostRoot 'host.json'
$config = Get-Content -LiteralPath $configPath -Raw | ConvertFrom-Json
$pwsh = Join-Path $hostRoot 'runtime/powershell/pwsh.exe'
$controller = Join-Path $hostRoot 'windows-host.ps1'
$sourceLib = Join-Path $gameRoot 'server/build/install/server/lib'
$runtime = @{}; $keys = @{}
$stopped = $false; $migrationAttempted = $false; $healthy = $false
$upgradeDirectory = $null

function Within-Host([string]$path) {
    $full = [IO.Path]::GetFullPath($path)
    if (!$full.StartsWith($hostRoot + '\', [StringComparison]::OrdinalIgnoreCase)) { throw 'Path escaped the installed host' }
    return $full
}
function Read-Protected([string]$path) {
    $value = (Get-Content -LiteralPath $path -Raw).Trim() | ConvertTo-SecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($value)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) | ConvertFrom-Json -AsHashtable }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $value.Dispose() }
}
function Run-Child([string]$executable, [string[]]$arguments, [hashtable]$variables, [string]$label) {
    $info = [Diagnostics.ProcessStartInfo]::new($executable)
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true; $info.WindowStyle = 'Hidden'
    $info.WorkingDirectory = $hostRoot; $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    foreach ($arg in $arguments) { $info.ArgumentList.Add($arg) }
    foreach ($name in $variables.Keys) { $info.Environment[$name] = [string]$variables[$name] }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info
    if (!$process.Start()) { throw "Could not start upgrade step: $label" }
    $output = $process.StandardOutput.ReadToEndAsync(); $errors = $process.StandardError.ReadToEndAsync()
    try {
        if (!$process.WaitForExit(30000)) { $process.Kill($true); $process.WaitForExit(); throw "Upgrade step timed out: $label" }
        if ($upgradeDirectory) {
            [IO.File]::WriteAllText((Join-Path $upgradeDirectory "$label.log"), $output.GetAwaiter().GetResult())
            [IO.File]::WriteAllText((Join-Path $upgradeDirectory "$label.err.log"), $errors.GetAwaiter().GetResult())
        }
        if ($process.ExitCode -ne 0) { throw "Upgrade step failed: $label. Logs remain in the protected host directory." }
        return $output.GetAwaiter().GetResult().Trim()
    } finally { $process.Dispose() }
}
function Active-RoomCount {
    $pgArgs = @('-X','--no-password','-h','127.0.0.1','-p','55433','-U','tambola_main_app','-d','tambola_local','-At','-v','ON_ERROR_STOP=1',
        '-c',"SELECT count(*) FROM rooms WHERE phase IN ('LOBBY','ACTIVE') AND expires_at > (extract(epoch from clock_timestamp()) * 1000)::bigint")
    return [int](Run-Child (Join-Path $config.postgresBin 'psql.exe') $pgArgs @{PGPASSWORD=$runtime.TAMBOLA_DATABASE_PASSWORD} 'room-inventory')
}
function Lock-Free([string]$name) {
    try { $handle = [IO.File]::Open((Join-Path $hostRoot $name), 'OpenOrCreate', 'ReadWrite', 'None'); $handle.Dispose(); return $true }
    catch [IO.IOException] { return $false }
}
function Control([string]$action) {
    Run-Child $pwsh @('-NoLogo','-NoProfile','-NonInteractive','-File',$controller,'-Action',$action,'-Directory',$hostRoot) @{} "host-$action" | Out-Null
}

try {
    $head = (& git -C $gameRoot rev-parse HEAD | Out-String).Trim()
    if ($LASTEXITCODE -ne 0 -or $head -ne $SourceCommit) { throw 'Build commit does not match the checkout' }
    if ((& git -C $gameRoot status --porcelain | Out-String).Trim()) { throw 'Commit the reviewed candidate before deployment' }
    foreach ($path in @($config.java,$config.postgresBin,$config.postgresData,$config.serviceLib,$config.caddy,$config.caddyFile)) { Within-Host $path | Out-Null }
    $modules = Run-Child $config.java @('--list-modules') @{} 'java-modules'
    if ($modules -notmatch '(?m)^jdk\.jfr@') { throw 'The installed Java runtime needs jdk.jfr for purchase timing. Rebuild the host runtime before stopping the service.' }
    if ($config.postgresPort -ne 55433 -or $config.serverPort -ne 18080) { throw 'Unexpected installed host ports' }
    $runtime = Read-Protected (Join-Path $hostRoot 'runtime.secrets')
    if ($runtime.TAMBOLA_DATABASE_URL -ne 'jdbc:postgresql://127.0.0.1:55433/tambola_local' -or
        $runtime.TAMBOLA_DELETION_DATABASE_URL -ne 'jdbc:postgresql://127.0.0.1:55433/tambola_local_journal' -or
        $runtime.TAMBOLA_DATABASE_USER -ne 'tambola_main_app' -or $runtime.TAMBOLA_DELETION_DATABASE_USER -ne 'tambola_journal_app') { throw 'Unexpected host database identity' }
    $jars = @(Get-ChildItem -LiteralPath $sourceLib -Filter '*.jar' -File)
    if ($jars.Count -lt 10) { throw 'Run server:installDist for the committed source first' }
    foreach ($module in @('server','domain','protocol')) {
        $built = Join-Path $gameRoot "$module/build/libs/$module.jar"
        if ((Get-FileHash -LiteralPath $built).Hash -ne (Get-FileHash -LiteralPath (Join-Path $sourceLib "$module.jar")).Hash) { throw 'Installed distribution differs from build output' }
    }
    if ((Active-RoomCount) -ne 0) { throw 'An unfinished game exists. Finish it before upgrading.' }
    if (!$Apply) { Write-Output 'Preflight passed: committed distribution, expected private host, no unfinished rooms. No serving or database changes made.'; return }
    $stamp = [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss')
    $upgradeDirectory = Within-Host (Join-Path $hostRoot "upgrades/$stamp-$($SourceCommit.Substring(0,12))")
    if (Test-Path -LiteralPath $upgradeDirectory) { throw 'Upgrade directory already exists' }
    New-Item -ItemType Directory -Path $upgradeDirectory | Out-Null
    $stagedLib = Within-Host (Join-Path $upgradeDirectory 'lib')
    Copy-Item -LiteralPath $sourceLib -Destination $stagedLib -Recurse
    Copy-Item -LiteralPath $configPath -Destination (Join-Path $upgradeDirectory 'host.before.json')
    $manifest = @($jars | ForEach-Object {
        $sourceHash = (Get-FileHash -LiteralPath $_.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        if ($sourceHash -ne (Get-FileHash -LiteralPath (Join-Path $stagedLib $_.Name) -Algorithm SHA256).Hash.ToLowerInvariant()) { throw 'Staged service checksum mismatch' }
        @{name=$_.Name;sha256=$sourceHash;bytes=$_.Length}
    })
    @{sourceCommit=$SourceCommit;files=$manifest} | ConvertTo-Json -Depth 4 | Set-Content -LiteralPath (Join-Path $upgradeDirectory 'service-manifest.json')
    Write-Output 'Candidate staged and verified; stopping the idle host.'
    Control 'Stop'; $stopped = $true
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        $listeners = Get-NetTCPConnection -LocalPort 18080,8443 -State Listen -ErrorAction SilentlyContinue
        if (!$listeners -and (Lock-Free 'watch.lock') -and (Lock-Free 'supervisor.lock')) { break }
        if ([DateTime]::UtcNow -ge $deadline) { throw 'Host did not finish stopping' }
        Start-Sleep -Milliseconds 250
    } while ($true)
    if ((Active-RoomCount) -ne 0) { throw 'A game appeared during preflight; old host will resume without migration' }
    $backupStarted = [DateTime]::UtcNow
    Run-Child $pwsh @('-NoLogo','-NoProfile','-NonInteractive','-File',(Join-Path $hostRoot 'windows-backup.ps1'),'-Directory',$hostRoot) @{} 'backup' | Out-Null
    $backupManifest = Get-ChildItem -LiteralPath (Join-Path $hostRoot 'backups') -File -Filter '*.json' |
        Where-Object { $_.Name -match '^\d{8}-\d{6}\.json$' -and $_.LastWriteTimeUtc -ge $backupStarted } | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    if (!$backupManifest) { throw 'No fresh completed backup pair' }
    $backup = Get-Content -LiteralPath $backupManifest.FullName -Raw | ConvertFrom-Json
    if ($backup.files.Count -ne 2) { throw 'Incomplete backup pair' }
    $retainedBackups = Within-Host (Join-Path $upgradeDirectory 'backups')
    New-Item -ItemType Directory -Path $retainedBackups | Out-Null
    foreach ($entry in $backup.files) {
        if ($entry.name -notmatch '^\d{8}-\d{6}-(primary|journal)\.dump$') { throw 'Unexpected backup filename' }
        $file = Within-Host (Join-Path (Join-Path $hostRoot 'backups') $entry.name)
        if ((Get-FileHash -LiteralPath $file).Hash.ToLowerInvariant() -ne $entry.sha256) { throw 'Backup checksum mismatch' }
        Copy-Item -LiteralPath $file -Destination $retainedBackups
    }
    Copy-Item -LiteralPath $backupManifest.FullName -Destination $retainedBackups
    Write-Output 'Both fresh database archives retained with the upgrade; applying migrations.'
    $keys = Read-Protected (Join-Path $hostRoot 'provision.secrets')
    $migration = $runtime.Clone()
    $migration.TAMBOLA_DATABASE_USER='tambola_main_owner'; $migration.TAMBOLA_DATABASE_PASSWORD=$keys.mainOwner
    $migration.TAMBOLA_DELETION_DATABASE_USER='tambola_journal_owner'; $migration.TAMBOLA_DELETION_DATABASE_PASSWORD=$keys.journalOwner
    $migrationAttempted = $true
    try { Run-Child $config.java @('-cp',(Join-Path $stagedLib '*'),'io.github.sbshrey.tambola.server.ServerKt','--migrate') $migration 'migrations' | Out-Null }
    finally { $migration.Clear() }
    foreach ($kind in @('main','journal')) {
        $database = if ($kind -eq 'main') {'tambola_local'} else {'tambola_local_journal'}
        $resource = if ($kind -eq 'main') {'primary'} else {'journal'}
        Run-Child (Join-Path $config.postgresBin 'psql.exe') @('-X','--no-password','-h','127.0.0.1','-p','55433','-U',"tambola_$($kind)_owner",'-d',$database,
            '-v','ON_ERROR_STOP=1','-v',"database=$database",'-v',"runtime_role=tambola_$($kind)_app",'-f',(Join-Path $gameRoot "server/src/main/resources/db/permissions/$resource.sql")) @{PGPASSWORD=$keys["$($kind)Owner"]} "$kind-grants" | Out-Null
    }
    $config.serviceLib = $stagedLib
    $config | Add-Member -NotePropertyName sourceCommit -NotePropertyValue $SourceCommit -Force
    $nextConfig = Within-Host (Join-Path $hostRoot 'host.next.json')
    $config | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $nextConfig
    Move-Item -LiteralPath $nextConfig -Destination (Within-Host $configPath) -Force
    $restartAt = [DateTime]::UtcNow
    Control 'Start'
    $deadline = [DateTime]::UtcNow.AddSeconds(45)
    do {
        Start-Sleep -Milliseconds 500
        try {
            $state = Get-Content -LiteralPath (Join-Path $hostRoot 'state.json') -Raw | ConvertFrom-Json
            $server = $state.children | Where-Object role -eq 'server'
            $process = Get-Process -Id $server.pid -ErrorAction Stop
            if ($state.status -eq 'ready' -and ([DateTimeOffset]$state.checkedAt).UtcDateTime -ge $restartAt -and
                $process.Path -eq $config.java -and $process.StartTime.ToUniversalTime().Ticks -eq ([DateTimeOffset]$server.startedAt).UtcTicks -and
                $process.StartTime.ToUniversalTime() -ge $restartAt -and
                (Invoke-WebRequest -Uri 'http://127.0.0.1:18080/health/ready' -TimeoutSec 3).StatusCode -eq 200) { $healthy=$true; break }
        } catch { }
    } while ([DateTime]::UtcNow -lt $deadline)
    if (!$healthy) { throw 'Updated host did not become healthy; retain the live journal and investigate protected logs' }
    @{sourceCommit=$SourceCommit;completedAt=[DateTime]::UtcNow.ToString('o');origin=$config.origin;backupPair=$backupManifest.Name;
        backendReady=$true;freshServerProcess=$true;restrictedStartup=$true;tlsClientAndCoinWriteRead='pending external verification'} |
        ConvertTo-Json | Set-Content -LiteralPath (Join-Path $upgradeDirectory 'deployment.json')
    Write-Output "Updated host ready. Source: $SourceCommit. Verify TLS coin transactions and the matching LAN APK next."
} finally {
    $runtime.Clear(); $keys.Clear()
    if ($stopped -and !$migrationAttempted) { Control 'Start' }
    if ($migrationAttempted -and !$healthy) {
        Write-Warning 'Migration was attempted. Old binaries may reject the new schema; no automatic downgrade or database restore was attempted.'
    }
}
