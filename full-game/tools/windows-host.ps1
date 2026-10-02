#requires -Version 7.0
param(
    [ValidateSet('Watch', 'Run', 'Start', 'Stop', 'Status', 'InstallStartup', 'RemoveStartup')][string]$Action = 'Status',
    [string]$Directory = (Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost')
)
$ErrorActionPreference = 'Stop'
$hostDirectory = [IO.Path]::GetFullPath($Directory).TrimEnd('\')
$configuration = Get-Content -LiteralPath (Join-Path $hostDirectory 'host.json') -Raw | ConvertFrom-Json
$statePath = Join-Path $hostDirectory 'state.json'
$stopPath = Join-Path $hostDirectory 'stop.request'
$startupPath = Join-Path ([Environment]::GetFolderPath('Startup')) 'Tambola Together Host.lnk'
$scriptPath = Join-Path $hostDirectory 'windows-host.ps1'
$pwsh = Join-Path $hostDirectory 'runtime/powershell/pwsh.exe'
if (!(Test-Path -LiteralPath $pwsh)) { throw 'Provision a pinned PowerShell runtime before starting the host' }

function Write-HostState($value) {
    $temporary = Join-Path $hostDirectory 'state.pending.json'
    $value | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $temporary -Encoding utf8
    Move-Item -LiteralPath $temporary -Destination $statePath -Force
}
function Stop-VerifiedChild($child) {
    $process = Get-Process -Id $child.pid -ErrorAction SilentlyContinue
    if ($null -ne $process -and $process.Path -eq $child.path -and
        $process.StartTime.ToUniversalTime().Ticks -eq ([DateTimeOffset]$child.startedAt).UtcTicks) { $process.Kill($true); $process.WaitForExit(10000) | Out-Null }
}
if ($Action -eq 'Status') {
    if (Test-Path -LiteralPath $statePath) { Get-Content -LiteralPath $statePath }
    else { '{"status":"not-started"}' }
    exit
}
if ($Action -eq 'Stop') { [IO.File]::WriteAllText($stopPath, 'stop'); exit }
if ($Action -eq 'RemoveStartup') {
    if (Test-Path -LiteralPath $startupPath) { Remove-Item -LiteralPath $startupPath }
    exit
}
if ($Action -eq 'InstallStartup') {
    $shell = New-Object -ComObject WScript.Shell
    $link = $shell.CreateShortcut($startupPath)
    $link.TargetPath = $pwsh
    $link.Arguments = "-NoLogo -NoProfile -NonInteractive -WindowStyle Hidden -File `"$scriptPath`" -Action Start -Directory `"$hostDirectory`""
    $link.WorkingDirectory = $hostDirectory; $link.WindowStyle = 7; $link.Save()
    Write-Output 'Installed current-user login startup. This is not a boot-before-login system service.'
    exit
}
if ($Action -eq 'Start') {
    if (Test-Path -LiteralPath $stopPath) { Remove-Item -LiteralPath $stopPath }
    Start-Process -FilePath $pwsh -WindowStyle Hidden -ArgumentList @('-NoLogo', '-NoProfile', '-NonInteractive', '-WindowStyle', 'Hidden', '-File', "`"$scriptPath`"", '-Action', 'Watch', '-Directory', "`"$hostDirectory`"")
    exit
}
if ($Action -eq 'Watch') {
    try { $watchLock = [IO.File]::Open((Join-Path $hostDirectory 'watch.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
    catch [IO.IOException] { exit }
    try {
        while (!(Test-Path -LiteralPath $stopPath)) {
            $worker = Start-Process -FilePath $pwsh -WindowStyle Hidden -PassThru -ArgumentList @('-NoLogo','-NoProfile','-NonInteractive','-WindowStyle','Hidden','-File',"`"$scriptPath`"",'-Action','Run','-Directory',"`"$hostDirectory`"")
            $worker.WaitForExit(); $worker.Dispose()
            if (!(Test-Path -LiteralPath $stopPath)) { Start-Sleep -Seconds 5 }
        }
    } finally { $watchLock.Dispose() }
    exit
}

# A file lock allows exactly one supervisor. It disappears automatically after a crash.
try { $supervisorLock = [IO.File]::Open((Join-Path $hostDirectory 'supervisor.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
catch [IO.IOException] { exit }
$children = @{}
$environment = @{}
$backup = $null
function Start-Owned([string]$role, [string]$executable, [string[]]$arguments, [hashtable]$variables) {
    $timestamp = Get-Date -Format 'yyyyMMdd-HHmmss-fff'
    $info = [Diagnostics.ProcessStartInfo]::new($executable)
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true; $info.WindowStyle = 'Hidden'
    $info.WorkingDirectory = $hostDirectory
    $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    foreach ($argument in $arguments) { $info.ArgumentList.Add($argument) }
    foreach ($key in $variables.Keys) { $info.Environment[$key] = [string]$variables[$key] }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info
    if (!$process.Start()) { throw "Could not start $role" }
    # Allow diagnostics to be read while the service is running. Disable the extra file buffer
    # so short callback failures are visible immediately, without restarting the game server.
    $output = [IO.FileStream]::new((Join-Path $hostDirectory "logs/$role-$timestamp.log"), 'Create', 'Write', 'Read', 1)
    $errorLog = [IO.FileStream]::new((Join-Path $hostDirectory "logs/$role-$timestamp.err.log"), 'Create', 'Write', 'Read', 1)
    $streams = @($process.StandardOutput.BaseStream.CopyToAsync($output), $process.StandardError.BaseStream.CopyToAsync($errorLog))
    return @{process=$process; output=$output; errorLog=$errorLog; streams=$streams; role=$role;
        pid=$process.Id; path=$executable; startedAt=$process.StartTime.ToUniversalTime().ToString('o')}
}
function Close-Owned($child) {
    if (!$child.process.HasExited) { Stop-VerifiedChild $child }
    foreach ($task in $child.streams) { $task.Wait(2000) | Out-Null }
    $child.output.Dispose(); $child.errorLog.Dispose(); $child.process.Dispose()
}
function Update-LanBinding {
    $origin = [Uri]$configuration.origin
    if ($origin.Port -ne 8443 -or $origin.HostNameType -ne [UriHostNameType]::IPv4) { return }
    $current = Get-NetIPAddress -AddressFamily IPv4 -IPAddress $origin.Host -ErrorAction SilentlyContinue |
        Where-Object AddressState -EQ Preferred | Select-Object -First 1
    if ($current) { return }

    # DHCP can assign a new address before this current-user host starts at sign-in.
    $address = $null
    $routes = Get-NetRoute -AddressFamily IPv4 -DestinationPrefix '0.0.0.0/0' -ErrorAction SilentlyContinue |
        Where-Object State -EQ Alive | Sort-Object { $_.RouteMetric + $_.InterfaceMetric }
    foreach ($route in $routes) {
        $address = Get-NetIPAddress -AddressFamily IPv4 -InterfaceIndex $route.InterfaceIndex -ErrorAction SilentlyContinue |
            Where-Object { $_.AddressState -eq 'Preferred' -and $_.IPAddress -match '^(10\.|192\.168\.|172\.(1[6-9]|2[0-9]|3[01])\.)' } |
            Select-Object -First 1
        if ($address) { break }
    }
    if (!$address) { throw 'No preferred private LAN address is ready' }

    $caddy = Get-Content -LiteralPath $configuration.caddyFile -Raw
    $sitePattern = '(?m)^https://(?:\d{1,3}\.){3}\d{1,3}:8443 \{\r?$'
    $bindPattern = '(?m)^    bind (?:\d{1,3}\.){3}\d{1,3}\r?$'
    if ([regex]::Matches($caddy, $sitePattern).Count -ne 1 -or [regex]::Matches($caddy, $bindPattern).Count -ne 1) {
        throw 'LAN Caddyfile differs from the provisioned binding'
    }
    $newOrigin = "https://$($address.IPAddress):8443"
    $caddy = [regex]::Replace($caddy, $sitePattern, "$newOrigin {")
    $caddy = [regex]::Replace($caddy, $bindPattern, "    bind $($address.IPAddress)")
    $pendingCaddy = "$($configuration.caddyFile).pending"
    $pendingConfig = Join-Path $hostDirectory 'host.pending.json'
    $caddy | Set-Content -LiteralPath $pendingCaddy
    Move-Item -LiteralPath $pendingCaddy -Destination $configuration.caddyFile -Force
    $configuration.origin = $newOrigin
    $configuration | ConvertTo-Json -Depth 5 | Set-Content -LiteralPath $pendingConfig
    Move-Item -LiteralPath $pendingConfig -Destination (Join-Path $hostDirectory 'host.json') -Force
}
function Ensure-Postgres {
    $ready = Start-Owned 'postgres-ready' (Join-Path $configuration.postgresBin 'pg_isready.exe') @('-h', '127.0.0.1', '-p', [string]$configuration.postgresPort) @{}
    try { $available = $ready.process.WaitForExit(5000) -and $ready.process.ExitCode -eq 0 }
    finally { Close-Owned $ready }
    if ($available) { return }
    $starter = Start-Owned 'postgres-start' (Join-Path $configuration.postgresBin 'pg_ctl.exe') @('-D', $configuration.postgresData, '-l', (Join-Path $hostDirectory 'logs/postgres.log'), '-w', '-t', '12', 'start') @{}
    try {
        if (!$starter.process.WaitForExit(15000) -or $starter.process.ExitCode -ne 0) { throw 'Database startup failed' }
    } finally { Close-Owned $starter }
}
try {
    Update-LanBinding
    if (Test-Path -LiteralPath $statePath) {
        $old = Get-Content -LiteralPath $statePath -Raw | ConvertFrom-Json
        foreach ($child in $old.children) { Stop-VerifiedChild $child }
    }
    $encrypted = (Get-Content -LiteralPath (Join-Path $hostDirectory 'runtime.secrets') -Raw).Trim() | ConvertTo-SecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($encrypted)
    try { $environment = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) | ConvertFrom-Json -AsHashtable }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $encrypted.Dispose() }
    Ensure-Postgres
    $failures = 0
    $lastBackup = Get-ChildItem -LiteralPath (Join-Path $hostDirectory 'backups') -Filter '*.json' -File |
        Where-Object Name -Match '^\d{8}-\d{6}\.json$' | Sort-Object LastWriteTimeUtc -Descending | Select-Object -First 1
    $nextBackupAt = if ($lastBackup) { $lastBackup.LastWriteTimeUtc.AddHours(24) } else { [DateTime]::UtcNow }
    $backupStatus = if ($lastBackup) { 'archived' } else { 'pending' }
    while (!(Test-Path -LiteralPath $stopPath)) {
        foreach ($role in @('server', 'tls')) {
            if ($children.ContainsKey($role) -and $children[$role].process.HasExited) { Close-Owned $children[$role]; $children.Remove($role) }
            if (!$children.ContainsKey($role)) {
                $children[$role] = if ($role -eq 'server') {
                    Start-Owned $role $configuration.java @('-Xms64m', '-Xmx384m', '-cp', (Join-Path $configuration.serviceLib '*'), 'io.github.sbshrey.tambola.server.ServerKt') $environment
                } else {
                    Start-Owned $role $configuration.caddy @('run', '--config', $configuration.caddyFile, '--adapter', 'caddyfile') @{'XDG_DATA_HOME'=(Join-Path $hostDirectory 'tls-data'); 'XDG_CONFIG_HOME'=(Join-Path $hostDirectory 'tls-config')}
                }
            }
        }
        $backendReady = $false; $tlsReady = $false
        try { $backendReady = (Invoke-WebRequest -Uri "http://127.0.0.1:$($configuration.serverPort)/health/ready" -TimeoutSec 3).StatusCode -eq 200 } catch {}
        $socket = [Net.Sockets.TcpClient]::new()
        try {
            $origin = [Uri]$configuration.origin
            $tlsReady = $socket.ConnectAsync($origin.Host, $origin.Port).Wait(500) -and $socket.Connected -and !$children.tls.process.HasExited
        } catch {} finally { $socket.Dispose() }
        $healthy = $backendReady -and $tlsReady
        $failures = if ($backendReady) { 0 } else { $failures + 1 }
        if ($backup -and $backup.process.HasExited) {
            $backupStatus = if ($backup.process.ExitCode -eq 0) { 'archived' } else { 'failed' }
            Close-Owned $backup; $backup = $null
            $nextBackupAt = if ($backupStatus -eq 'archived') { [DateTime]::UtcNow.AddHours(24) } else { [DateTime]::UtcNow.AddMinutes(15) }
        }
        if ($healthy -and !$backup -and [DateTime]::UtcNow -ge $nextBackupAt) {
            $backup = Start-Owned 'backup' $pwsh @('-NoLogo','-NoProfile','-NonInteractive','-File',(Join-Path $hostDirectory 'windows-backup.ps1'),'-Directory',$hostDirectory) @{}
            $backupStatus = 'running'
        }
        Write-HostState @{status= $(if ($healthy) {'ready'} else {'starting'}); supervisorPid=$PID; checkedAt=[DateTime]::UtcNow.ToString('o'); origin=$configuration.origin;
            backendReady=$backendReady; tlsReady=$tlsReady; backup=$backupStatus; nextBackupAt=$nextBackupAt.ToString('o');
            children=@(@($children.Values) + @($backup) | Where-Object { $null -ne $_ } | ForEach-Object { @{role=$_.role; pid=$_.pid; path=$_.path; startedAt=$_.startedAt} })}
        if ($failures -ge 3) {
            Close-Owned $children.server; $children.Remove('server'); $failures = 0
            Ensure-Postgres
        }
        Start-Sleep -Seconds 5
    }
} catch {
    # Record the error category only; exception text can contain configuration details.
    Write-HostState @{status='failed'; checkedAt=[DateTime]::UtcNow.ToString('o'); errorType=$_.Exception.GetType().Name}
} finally {
    if ($backup) { try { Close-Owned $backup } catch {} }
    foreach ($child in $children.Values) { try { Close-Owned $child } catch {} }
    $environment.Clear()
    if (Test-Path -LiteralPath $stopPath) { Write-HostState @{status='stopped'; checkedAt=[DateTime]::UtcNow.ToString('o')} }
    $supervisorLock.Dispose()
}
