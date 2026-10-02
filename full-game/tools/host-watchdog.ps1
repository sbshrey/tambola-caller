#requires -Version 7.0
param(
    [ValidateSet('Check','Install','Remove')][string]$Action = 'Check'
)
$ErrorActionPreference = 'Stop'
$taskName = 'Tambola Jalsa Host Watchdog'
if ($Action -eq 'Remove') {
    Unregister-ScheduledTask -TaskName $taskName -Confirm:$false -ErrorAction SilentlyContinue
    exit
}
if ($Action -eq 'Install') {
    $installed = Join-Path $env:LOCALAPPDATA 'TambolaTogetherPublicHost/host-watchdog.ps1'
    if (!(Test-Path -LiteralPath $installed)) { throw 'Install the watchdog script first' }
    $account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
    $pwsh = (Get-Process -Id $PID).Path
    $taskAction = New-ScheduledTaskAction -Execute $pwsh -Argument "-NoProfile -NonInteractive -WindowStyle Hidden -File `"$installed`" -Action Check"
    $triggers = @(
        (New-ScheduledTaskTrigger -AtLogOn -User $account),
        (New-ScheduledTaskTrigger -Once -At (Get-Date).AddMinutes(1) -RepetitionInterval (New-TimeSpan -Minutes 2))
    )
    $principal = New-ScheduledTaskPrincipal -UserId $account -LogonType Interactive -RunLevel Limited
    $settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
        -MultipleInstances IgnoreNew -ExecutionTimeLimit (New-TimeSpan -Minutes 1)
    Register-ScheduledTask -TaskName $taskName -Action $taskAction -Trigger $triggers -Principal $principal -Settings $settings -Force | Out-Null
    Write-Output 'Installed current-user login and two-minute watchdog. Windows sign-in is required.'
    exit
}

foreach ($target in @(
    @{ folder='TambolaTogetherHost'; script='windows-host.ps1' },
    @{ folder='TambolaTogetherPublicHost'; script='public-host.ps1' }
)) {
    $directory = Join-Path $env:LOCALAPPDATA $target.folder
    $script = Join-Path $directory $target.script
    if (!(Test-Path -LiteralPath $script) -or (Test-Path -LiteralPath (Join-Path $directory 'stop.request'))) { continue }
    # A held lock proves a live watcher; never infer liveness from an old status file or PID.
    try { $watchLock = [IO.File]::Open((Join-Path $directory 'watch.lock'), 'OpenOrCreate', 'ReadWrite', 'None') }
    catch [IO.IOException] { continue }
    $watchLock.Dispose()
    & $script -Action Start -Directory $directory
}

# The permanent play hostname uses a separate scheduled tunnel task. Task Scheduler
# can leave it Ready after cloudflared exits, even when restart settings are present.
$namedTunnel = Get-ScheduledTask -TaskName 'Tambola Jalsa Named Tunnel' -ErrorAction SilentlyContinue
if ($namedTunnel -and $namedTunnel.State -eq 'Ready' -and
    (Test-Path -LiteralPath (Join-Path $env:LOCALAPPDATA 'TambolaJalsaTunnel/token'))) {
    Start-ScheduledTask -TaskName 'Tambola Jalsa Named Tunnel'
}
