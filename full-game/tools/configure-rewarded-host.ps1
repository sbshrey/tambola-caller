#requires -Version 7.0
[CmdletBinding()]
param(
    [ValidatePattern('^ca-app-pub-[0-9]{16}/[0-9]{10}$')][string]$RewardUnit = 'ca-app-pub-1312548197553464/9960291000',
    [switch]$Apply,
    [string]$Directory = (Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost')
)
$ErrorActionPreference = 'Stop'
if ($RewardUnit.StartsWith('ca-app-pub-3940256099942544/')) { throw 'The server must not credit Google demo units.' }
$hostRoot = [IO.Path]::GetFullPath($Directory).TrimEnd('\')
$config = Get-Content -LiteralPath (Join-Path $hostRoot 'host.json') -Raw | ConvertFrom-Json
if ($config.postgresPort -ne 55433 -or $config.serverPort -ne 18080) { throw 'Unexpected installed host ports.' }
$secretPath = Join-Path $hostRoot 'runtime.secrets'
$pwsh = Join-Path $hostRoot 'runtime/powershell/pwsh.exe'
$controller = Join-Path $hostRoot 'windows-host.ps1'
$lock = [IO.File]::Open((Join-Path $hostRoot 'reward-config.lock'), 'OpenOrCreate', 'ReadWrite', 'None')
$runtime = @{}; $stopped = $false; $changed = $false; $backup = $null
function Run-Private([string]$Executable, [string[]]$Arguments, [hashtable]$Variables = @{}) {
    $info = [Diagnostics.ProcessStartInfo]::new($Executable)
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true; $info.WindowStyle = 'Hidden'
    $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    foreach ($argument in $Arguments) { $info.ArgumentList.Add($argument) }
    foreach ($key in $Variables.Keys) { $info.Environment[$key] = [string]$Variables[$key] }
    $process = [Diagnostics.Process]::new(); $process.StartInfo = $info
    try {
        if (!$process.Start()) { throw 'Host configuration command could not start.' }
        $output = $process.StandardOutput.ReadToEndAsync(); $errors = $process.StandardError.ReadToEndAsync()
        if (!$process.WaitForExit(30000)) { $process.Kill($true); $process.WaitForExit(); throw 'Host configuration command timed out.' }
        if ($process.ExitCode -ne 0) { throw 'Host configuration command failed; private output withheld.' }
        return $output.GetAwaiter().GetResult().Trim()
    } finally { $process.Dispose() }
}
function Control([string]$action) {
    Run-Private $pwsh @('-NoProfile','-NonInteractive','-File',$controller,'-Action',$action,'-Directory',$hostRoot) | Out-Null
}
function Wait-Ready {
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        try { if ((Invoke-WebRequest 'http://127.0.0.1:18080/health/ready' -TimeoutSec 2).StatusCode -eq 200) { return } } catch {}
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Host did not become ready after reward configuration.'
}
function Wait-Stopped {
    $deadline = [DateTime]::UtcNow.AddSeconds(30)
    do {
        $free = $true
        foreach ($name in @('watch.lock','supervisor.lock')) {
            try { $handle = [IO.File]::Open((Join-Path $hostRoot $name), 'OpenOrCreate','ReadWrite','None'); $handle.Dispose() }
            catch [IO.IOException] { $free = $false }
        }
        if ($free) { return }
        Start-Sleep -Milliseconds 500
    } while ([DateTime]::UtcNow -lt $deadline)
    throw 'Host did not stop cleanly; configuration was not changed.'
}
try {
    $encrypted = (Get-Content -LiteralPath $secretPath -Raw).Trim() | ConvertTo-SecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($encrypted)
    try { $runtime = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) | ConvertFrom-Json -AsHashtable }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $encrypted.Dispose() }
    if ($runtime.TAMBOLA_DATABASE_URL -ne 'jdbc:postgresql://127.0.0.1:55433/tambola_local' -or $runtime.TAMBOLA_DATABASE_USER -ne 'tambola_main_app') { throw 'Unexpected host database identity.' }
    $count = Run-Private (Join-Path $config.postgresBin 'psql.exe') @('-X','--no-password','-h','127.0.0.1','-p','55433','-U','tambola_main_app','-d','tambola_local','-At','-v','ON_ERROR_STOP=1',
        '-c',"SELECT count(*) FROM rooms WHERE phase IN ('LOBBY','ACTIVE') AND expires_at > (extract(epoch from clock_timestamp()) * 1000)::bigint") @{PGPASSWORD=$runtime.TAMBOLA_DATABASE_PASSWORD}
    if ([int]$count -ne 0) { throw 'An unfinished game exists. Finish it before restarting the host.' }
    Wait-Ready
    if (!$Apply) { Write-Output "Reward configuration preflight passed for $RewardUnit. No host changes made."; return }
    if ($runtime.TAMBOLA_ADMOB_REWARD_UNIT -eq $RewardUnit) { Write-Output 'The requested reward unit is already configured.'; return }
    $backup = Join-Path $hostRoot ('runtime.before-ads-' + [DateTime]::UtcNow.ToString('yyyyMMdd-HHmmss') + '.secrets')
    Copy-Item -LiteralPath $secretPath -Destination $backup
    Control 'Stop'; $stopped = $true
    Wait-Stopped
    $runtime.TAMBOLA_ADMOB_REWARD_UNIT = $RewardUnit
    $secure = ($runtime | ConvertTo-Json -Compress) | ConvertTo-SecureString -AsPlainText -Force
    try { $protected = ConvertFrom-SecureString $secure } finally { $secure.Dispose() }
    $pending = Join-Path $hostRoot 'runtime.ads-pending.secrets'
    [IO.File]::WriteAllText($pending, $protected)
    Move-Item -LiteralPath $pending -Destination $secretPath -Force
    $changed = $true
    Control 'Start'; Wait-Ready; $stopped = $false
    Write-Output "Reward server configured for $RewardUnit; host health passed. The public tunnel was left running."
    Write-Output 'A real Google-signed verification callback must still pass before publishing a live-ad APK.'
} catch {
    if ($changed) { Control 'Stop'; Wait-Stopped; Copy-Item -LiteralPath $backup -Destination $secretPath -Force }
    if ($stopped) { Control 'Start' }
    throw
} finally { $runtime.Clear(); $lock.Dispose() }
