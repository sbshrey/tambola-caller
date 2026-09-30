#requires -Version 7.0
param(
    [string]$Directory = (Join-Path $env:LOCALAPPDATA 'TambolaJalsaTunnel'),
    [string]$Cloudflared = 'C:\Program Files (x86)\cloudflared\cloudflared.exe'
)
$ErrorActionPreference = 'Stop'
$tokenFile = Join-Path $Directory 'token'
if (!(Test-Path -LiteralPath $tokenFile) -or !(Test-Path -LiteralPath $Cloudflared)) {
    throw 'Install cloudflared and save the tunnel token in the restricted local directory first.'
}
# Credentials stay outside Git and are never passed as command-line values.
$account = [Security.Principal.WindowsIdentity]::GetCurrent().Name
& icacls $Directory /inheritance:r /grant:r "${account}:(OI)(CI)F" 'SYSTEM:(OI)(CI)F' | Out-Null
if ($LASTEXITCODE -ne 0) { throw 'Could not restrict tunnel directory permissions' }
$action = New-ScheduledTaskAction -Execute $Cloudflared -Argument "tunnel --no-autoupdate --protocol http2 --metrics 127.0.0.1:18083 run --token-file `"$tokenFile`""
$trigger = New-ScheduledTaskTrigger -AtLogOn -User $account
$principal = New-ScheduledTaskPrincipal -UserId $account -LogonType Interactive -RunLevel Limited
$settings = New-ScheduledTaskSettingsSet -StartWhenAvailable -AllowStartIfOnBatteries -DontStopIfGoingOnBatteries `
    -MultipleInstances IgnoreNew -ExecutionTimeLimit ([TimeSpan]::Zero) -RestartCount 999 -RestartInterval (New-TimeSpan -Minutes 1) -Hidden
Register-ScheduledTask -TaskName 'Tambola Jalsa Named Tunnel' -Action $action -Trigger $trigger -Principal $principal -Settings $settings -Force | Out-Null
Start-ScheduledTask -TaskName 'Tambola Jalsa Named Tunnel'
Write-Output 'Installed and started the named tunnel task. Windows sign-in is required after reboot; verify connector readiness separately.'
