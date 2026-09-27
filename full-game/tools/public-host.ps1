#requires -Version 7.0
param(
    [ValidateSet('Install','Start','Watch','Stop','Status','InstallStartup','RemoveStartup')][string]$Action = 'Status',
    [string]$Directory = (Join-Path $env:LOCALAPPDATA 'TambolaTogetherPublicHost')
)
$ErrorActionPreference = 'Stop'
$publicDirectory = [IO.Path]::GetFullPath($Directory)
$stopPath = Join-Path $publicDirectory 'stop.request'
$pwshPath = (Get-Process -Id $PID).Path
$startup = Join-Path ([Environment]::GetFolderPath('Startup')) 'Tambola Internet Beta.lnk'
if ($Action -eq 'Install') {
    $nodePath = (Get-Command node).Source
    $ghPath = (Get-Command gh).Source
    $cloudPath = (Get-Command cloudflared).Source
    New-Item -ItemType Directory -Path $publicDirectory -Force | Out-Null
    foreach ($name in @('public-host.ps1','public-host.mjs','public-gateway.mjs')) {
        Copy-Item -LiteralPath (Join-Path $PSScriptRoot $name) -Destination (Join-Path $publicDirectory $name) -Force
    }
    @{node=$nodePath;gh=$ghPath;cloudflared=$cloudPath;pwsh=$pwshPath} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $publicDirectory 'runtime.json')
    Write-Output 'Installed public beta supervisor. Start and InstallStartup are separate actions.'
    exit
}
if ($Action -eq 'Status') {
    $status = Join-Path $publicDirectory 'status.json'
    if (Test-Path -LiteralPath $status) { Get-Content -LiteralPath $status } else { '{"status":"not-started"}' }
    exit
}
if ($Action -eq 'Stop') { Set-Content -LiteralPath $stopPath -Value 'stop'; exit }
if ($Action -eq 'RemoveStartup') { if (Test-Path -LiteralPath $startup) { Remove-Item -LiteralPath $startup }; exit }
$runtime = Get-Content -LiteralPath (Join-Path $publicDirectory 'runtime.json') -Raw | ConvertFrom-Json
$script = Join-Path $publicDirectory 'public-host.ps1'
if ($Action -eq 'InstallStartup') {
    $shell = New-Object -ComObject WScript.Shell
    $shortcut = $shell.CreateShortcut($startup)
    $shortcut.TargetPath = $runtime.pwsh
    $shortcut.Arguments = "-NoProfile -NonInteractive -WindowStyle Hidden -File `"$script`" -Action Start -Directory `"$publicDirectory`""
    $shortcut.WindowStyle = 7; $shortcut.WorkingDirectory = $publicDirectory; $shortcut.Save()
    Write-Output 'Installed current-user login startup for the temporary Internet beta.'
    exit
}
if ($Action -eq 'Start') {
    if (Test-Path -LiteralPath $stopPath) { Remove-Item -LiteralPath $stopPath }
    Start-Process -FilePath $runtime.pwsh -WindowStyle Hidden -ArgumentList @('-NoProfile','-NonInteractive','-WindowStyle','Hidden','-File',"`"$script`"",'-Action','Watch','-Directory',"`"$publicDirectory`"")
    exit
}
try { $watchLock = [IO.File]::Open((Join-Path $publicDirectory 'watch.lock'),'OpenOrCreate','ReadWrite','None') }
catch [IO.IOException] { exit }
try {
    $env:TAMBOLA_GH = $runtime.gh
    $env:TAMBOLA_CLOUDFLARED = $runtime.cloudflared
    while (!(Test-Path -LiteralPath $stopPath)) {
        $worker = Start-Process -FilePath $runtime.node -WindowStyle Hidden -PassThru -ArgumentList @("`"$(Join-Path $publicDirectory 'public-host.mjs')`"", "`"$publicDirectory`"")
        while (!$worker.WaitForExit(1000)) {
            if (Test-Path -LiteralPath $stopPath) {
                if (!$worker.WaitForExit(25000)) { $worker.Kill($true); $worker.WaitForExit(5000) | Out-Null }
                break
            }
        }
        $worker.Dispose()
        if (!(Test-Path -LiteralPath $stopPath)) { Start-Sleep -Seconds 10 }
    }
} finally { $watchLock.Dispose() }
