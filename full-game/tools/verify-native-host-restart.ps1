#requires -Version 7.0
# Fault probe only for LanCoinGameTest's four-player QA round on the installed host.
[CmdletBinding()]
param([string]$Serial = 'emulator-5582')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$') { throw 'Dedicated emulator required' }
$root = Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost'
$config = Get-Content -LiteralPath (Join-Path $root 'host.json') -Raw | ConvertFrom-Json
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
$progress = (& $adb -s $Serial exec-out run-as io.github.sbshrey.tambola.game cat files/coin-lan-game.json | Out-String) | ConvertFrom-Json
if ($LASTEXITCODE -ne 0 -or $progress.stage -ne 'playing' -or $progress.calls -lt 20 -or $progress.calls -gt 60) { throw 'Expected native QA round is not in its middle calls' }
if ($config.postgresPort -ne 55433 -or $config.serverPort -ne 18080 -or $config.origin -ne $progress.origin) { throw 'Unexpected host identity' }
$protected = (Get-Content -LiteralPath (Join-Path $root 'runtime.secrets') -Raw).Trim() | ConvertTo-SecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($protected)
try { $runtime = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) | ConvertFrom-Json -AsHashtable }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $protected.Dispose() }
try {
    if ($runtime.TAMBOLA_DATABASE_URL -ne 'jdbc:postgresql://127.0.0.1:55433/tambola_local' -or $runtime.TAMBOLA_DATABASE_USER -ne 'tambola_main_app') { throw 'Unexpected database identity' }
    $sql = @'
WITH live AS (SELECT phase, payload::jsonb AS doc FROM rooms
WHERE phase IN ('LOBBY','ACTIVE') AND expires_at > (extract(epoch from clock_timestamp()) * 1000)::bigint)
SELECT count(*) = 1 AND bool_and(phase = 'ACTIVE' AND doc->'options'->>'coinGame' = 'true'
AND jsonb_array_length(doc->'members') = 4
AND (SELECT count(*) FROM jsonb_array_elements(doc->'members') m
     WHERE m->>'name' IN ('LAN QA Peer 1','LAN QA Peer 2','LAN QA Peer 3')) = 3) FROM live
'@
    $info = [Diagnostics.ProcessStartInfo]::new((Join-Path $config.postgresBin 'psql.exe'))
    $info.UseShellExecute = $false; $info.CreateNoWindow = $true; $info.WindowStyle = 'Hidden'
    $info.RedirectStandardOutput = $true; $info.RedirectStandardError = $true
    foreach ($arg in @('-X','--no-password','-h','127.0.0.1','-p','55433','-U','tambola_main_app','-d','tambola_local','-At','-v','ON_ERROR_STOP=1','-c',$sql)) { $info.ArgumentList.Add($arg) }
    $info.Environment['PGPASSWORD'] = $runtime.TAMBOLA_DATABASE_PASSWORD
    $query = [Diagnostics.Process]::Start($info)
    try {
        $output = $query.StandardOutput.ReadToEndAsync(); $errors = $query.StandardError.ReadToEndAsync()
        if (!$query.WaitForExit(10000)) { $query.Kill($true); throw 'QA inventory timeout' }
        if ($query.ExitCode -ne 0 -or $output.GetAwaiter().GetResult().Trim() -ne 't') { throw 'Refusing restart: host does not contain exactly the expected QA game' }
    } finally { $query.Dispose() }
} finally { $runtime.Clear() }
$state = Get-Content -LiteralPath (Join-Path $root 'state.json') -Raw | ConvertFrom-Json
if ($state.status -ne 'ready' -or ([DateTimeOffset]::UtcNow - [DateTimeOffset]$state.checkedAt).TotalSeconds -gt 15) { throw 'Fresh ready host required' }
$child = $state.children | Where-Object role -eq 'server'
$process = Get-Process -Id $child.pid
$identity = Get-CimInstance Win32_Process -Filter "ProcessId = $($child.pid)"
if ($process.Path -ne $config.java -or $process.Path -ne $child.path -or
    $process.StartTime.ToUniversalTime().Ticks -ne ([DateTimeOffset]$child.startedAt).UtcTicks -or
    !$identity.CommandLine.Contains($config.serviceLib)) { throw 'Server process identity mismatch' }
$started = [DateTimeOffset]::UtcNow
Stop-Process -Id $process.Id -Force
$deadline = $started.AddSeconds(45)
do {
    Start-Sleep -Milliseconds 500
    try {
        $next = Get-Content -LiteralPath (Join-Path $root 'state.json') -Raw | ConvertFrom-Json
        $server = $next.children | Where-Object role -eq 'server'
        $replacement = Get-Process -Id $server.pid -ErrorAction Stop
        if ($next.status -eq 'ready' -and $server.pid -ne $child.pid -and
            [DateTimeOffset]$server.startedAt -gt $started -and
            $replacement.Path -eq $config.java -and
            $replacement.StartTime.ToUniversalTime().Ticks -eq ([DateTimeOffset]$server.startedAt).UtcTicks -and
            (Invoke-WebRequest -Uri 'http://127.0.0.1:18080/health/ready' -TimeoutSec 3).StatusCode -eq 200) {
            $evidence = @{observedAt=[DateTimeOffset]::UtcNow.ToString('o');sourceCommit=$config.sourceCommit;role='server';
                originalCalls=$progress.calls;recoveryMs=[int]([DateTimeOffset]::UtcNow-$started).TotalMilliseconds;
                expectedQaGameOnly=$true;freshServerProcess=$true;backendReady=$true;nativeCompletion='See matching LanCoinGameTest report'}
            $evidence | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $PSScriptRoot '../.test-workspace/coin-native-host-restart.json')
            $evidence | ConvertTo-Json
            return
        }
    } catch { }
} while ([DateTimeOffset]::UtcNow -lt $deadline)
throw 'Owned server did not recover within 45 seconds'
