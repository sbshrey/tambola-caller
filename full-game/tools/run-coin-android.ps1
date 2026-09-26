[CmdletBinding()]
param([string]$Serial = 'emulator-5582', [string]$TestClass = 'CoinGameTest', [string]$Label = 'coin-alpha16-native')
$ErrorActionPreference = 'Stop'
if ($Serial -notmatch '^emulator-\d+$' -or $TestClass -notmatch '^[A-Za-z0-9_#]+$' -or $Label -notmatch '^[a-z0-9-]+$') { throw 'Invalid fixture argument' }
$repoRoot = Split-Path -Parent $PSScriptRoot
Set-Location -LiteralPath $repoRoot
if ($env:TAMBOLA_TEST_DATABASE_URL -notmatch '^jdbc:postgresql://127\.0\.0\.1:[0-9]+/tambola_test$' -or !$env:TAMBOLA_TEST_DATABASE_USER -or !$env:TAMBOLA_TEST_DATABASE_PASSWORD) { throw 'Explicit isolated test database credentials required' }
$adb = Join-Path $env:LOCALAPPDATA 'Android/Sdk/platform-tools/adb.exe'
if (Get-NetTCPConnection -LocalPort 8080,8081,8082 -State Listen -ErrorAction SilentlyContinue) { throw 'Fixture ports already owned' }
if ((& $adb -s $Serial reverse --list | Out-String) -match 'tcp:8080|tcp:8082') { throw 'Existing mappings must be preserved' }
$overrides = @{
    TAMBOLA_DATABASE_URL=$env:TAMBOLA_TEST_DATABASE_URL; TAMBOLA_DATABASE_USER=$env:TAMBOLA_TEST_DATABASE_USER
    TAMBOLA_DATABASE_PASSWORD=$env:TAMBOLA_TEST_DATABASE_PASSWORD; TAMBOLA_LOCAL_DEVELOPMENT='true'
    TAMBOLA_BIND_HOST='127.0.0.1'; PORT='8081'; TAMBOLA_PUBLIC_ORIGIN='http://127.0.0.1:8080'
    TAMBOLA_DELETION_DATABASE_URL=$null; TAMBOLA_DELETION_DATABASE_USER=$null; TAMBOLA_DELETION_DATABASE_PASSWORD=$null
    TAMBOLA_METRICS_TOKEN=$null; TAMBOLA_ANDROID_CERT_SHA256=$null; TAMBOLA_ANDROID_INSTALL_URL=$null
}
$previous = @{}
$service = $null; $proxy = $null; $mapped = @()
try {
    foreach ($name in $overrides.Keys) {
        $previous[$name] = [Environment]::GetEnvironmentVariable($name)
        if ($null -eq $overrides[$name]) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($name, $overrides[$name]) }
    }
    $service = Start-Process -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -ArgumentList @('-XX:ActiveProcessorCount=4','-Xmx512m','-cp','"server/build/install/server/lib/*"','io.github.sbshrey.tambola.server.ServerKt') -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput ".test-workspace/$Label-service.log" -RedirectStandardError ".test-workspace/$Label-service-error.log"
    $proxy = Start-Process -FilePath (Get-Command node).Source -ArgumentList 'tools/room-fault-proxy.mjs' -WorkingDirectory $repoRoot -WindowStyle Hidden -PassThru -RedirectStandardOutput ".test-workspace/$Label-proxy.log" -RedirectStandardError ".test-workspace/$Label-proxy-error.log"
    $ready = $false
    for ($attempt=0; $attempt -lt 100; $attempt++) {
        if ($service.HasExited -or $proxy.HasExited) { throw 'Owned fixture exited before readiness' }
        try { if ((Invoke-WebRequest -Uri 'http://127.0.0.1:8080/health/ready' -TimeoutSec 1).StatusCode -eq 200) { $ready=$true; break } } catch {}
        Start-Sleep -Milliseconds 250
    }
    if (!$ready) { throw 'Fixture never became ready' }
    foreach ($port in @('tcp:8080','tcp:8082')) {
        & $adb -s $Serial reverse $port $port
        if ($LASTEXITCODE -ne 0) { throw 'Mapping failed' }
        $mapped += $port
    }
    foreach ($apk in @('app/build/outputs/apk/debug/app-debug.apk','app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk')) {
        & $adb -s $Serial install -r $apk
        if ($LASTEXITCODE -ne 0) { throw 'Fixture APK installation failed' }
    }
    node tools/android-smoke.mjs --serial $Serial --online --fault-proxy --class "io.github.sbshrey.tambola.game.$TestClass" --label $Label
    if ($LASTEXITCODE -ne 0) { throw 'Native coin test failed' }
} finally {
    foreach ($port in $mapped) { & $adb -s $Serial reverse --remove $port }
    foreach ($owned in @($proxy, $service)) { if ($null -ne $owned -and !$owned.HasExited) { Stop-Process -Id $owned.Id; $owned.WaitForExit() } }
    foreach ($name in $previous.Keys) {
        if ($null -eq $previous[$name]) { Remove-Item -LiteralPath "Env:$name" -ErrorAction SilentlyContinue }
        else { [Environment]::SetEnvironmentVariable($name, $previous[$name]) }
    }
    Write-Output 'Owned fixture processes stopped; created mappings removed.'
}
