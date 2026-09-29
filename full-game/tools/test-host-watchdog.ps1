#requires -Version 7.0
$ErrorActionPreference = 'Stop'
$fixture = Join-Path ([IO.Path]::GetTempPath()) ('tambola-watchdog-test-' + [Guid]::NewGuid().ToString('N'))
$savedLocalAppData = $env:LOCALAPPDATA
$lock = $null
try {
    $env:LOCALAPPDATA = $fixture
    foreach ($pair in @(@('TambolaTogetherHost','windows-host.ps1'),@('TambolaTogetherPublicHost','public-host.ps1'))) {
        $directory = Join-Path $fixture $pair[0]
        New-Item -ItemType Directory -Path $directory -Force | Out-Null
        Set-Content -LiteralPath (Join-Path $directory $pair[1]) -Value 'param($Action,$Directory); Add-Content -LiteralPath (Join-Path $Directory "started.txt") -Value $Action'
    }
    $backend = Join-Path $fixture 'TambolaTogetherHost'
    $public = Join-Path $fixture 'TambolaTogetherPublicHost'
    # Backend is alive; public host was intentionally stopped. Neither should restart.
    $lock = [IO.File]::Open((Join-Path $backend 'watch.lock'),'OpenOrCreate','ReadWrite','None')
    Set-Content -LiteralPath (Join-Path $public 'stop.request') -Value 'stop'
    & (Join-Path $PSScriptRoot 'host-watchdog.ps1') -Action Check
    if ((Test-Path (Join-Path $backend 'started.txt')) -or (Test-Path (Join-Path $public 'started.txt'))) { throw 'Restarted live or deliberately stopped host' }
    $lock.Dispose(); $lock = $null
    & (Join-Path $PSScriptRoot 'host-watchdog.ps1') -Action Check
    if ((Get-Content (Join-Path $backend 'started.txt')) -ne 'Start') { throw 'Dead watcher not recovered' }
    if (Test-Path (Join-Path $public 'started.txt')) { throw 'Stop marker ignored' }
    Write-Output 'PASS: held lock prevents restart; released lock recovers watcher; deliberate Stop is respected.'
} finally {
    if ($lock) { $lock.Dispose() }
    $env:LOCALAPPDATA = $savedLocalAppData
    # The GUID fixture contains only the test scripts above; retain it for inspection.
}
