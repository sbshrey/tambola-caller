#requires -Version 7.0
param([string]$Serial = 'emulator-5554', [ValidateSet('fullRound','purchaseRefund','friendsRound','friendInvitation')][string]$Test = 'fullRound')
$ErrorActionPreference = 'Stop'
$root = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot '..'))
$adb = Join-Path $env:ANDROID_SDK_ROOT 'platform-tools/adb.exe'
$app = Join-Path $root 'app/build/outputs/apk/publicBeta/app-publicBeta.apk'
$driver = Join-Path $root 'macrobenchmark/build/outputs/apk/publicBeta/macrobenchmark-publicBeta.apk'
$evidence = Join-Path $root '.test-workspace/internet-beta'
New-Item -ItemType Directory -Path $evidence -Force | Out-Null
function Invoke-Adb([string[]]$Arguments) {
    $output = & $adb -s $Serial @Arguments
    if ($LASTEXITCODE -ne 0) { throw 'adb check failed' }
    return $output
}
$avd = (Invoke-Adb @('emu','avd','name')) -join "`n"
if ($avd -notmatch '^tambola_full_game_') { throw 'Use only the owned Tambola emulator' }
if ((Invoke-Adb @('shell','getprop','sys.boot_completed')).Trim() -ne '1') { throw 'Wait for the emulator to boot' }
if (Invoke-Adb @('reverse','--list')) { throw 'Remove fixture adb forwarding before public acceptance' }
Invoke-Adb @('install','-r',$app)
Invoke-Adb @('install','-r',$driver)
$testArgs = @('-s',$Serial,'shell','am','instrument','-w','-e','class',"io.github.sbshrey.tambola.benchmark.InternetBetaTest#$Test",'-e','tambolaInternetBeta','true','io.github.sbshrey.tambola.benchmark/androidx.test.runner.AndroidJUnitRunner')
$runner = Start-Process -FilePath $adb -ArgumentList $testArgs -WindowStyle Hidden -PassThru -RedirectStandardOutput (Join-Path $evidence 'instrumentation.txt') -RedirectStandardError (Join-Path $evidence 'instrumentation-error.txt')
if (!$runner.WaitForExit(900000)) { $runner.Kill($true); throw 'Internet beta test exceeded fifteen minutes; inspect and clean QA profiles' }
$transcript = Get-Content -LiteralPath (Join-Path $evidence 'instrumentation.txt') -Raw
$passed = $runner.ExitCode -eq 0 -and $transcript -match 'OK \(1 test\)'
foreach ($name in @('coin-release-journey.json','coin-release-results.png','coin-release-failure.png','coin-release-failure.xml','friends-public-ready.png','friends-replay-ready.png','friend-link-review.png','friend-link-joined.png','friend-link-occupied.png')) {
    $exists = & $adb -s $Serial shell run-as io.github.sbshrey.tambola.benchmark test -f "files/$name"
    if ($LASTEXITCODE -eq 0) {
        & $adb -s $Serial exec-out run-as io.github.sbshrey.tambola.benchmark cat "files/$name" > (Join-Path $evidence $name)
    }
}
@{passed=$passed;checkedAt=[DateTimeOffset]::UtcNow.ToString('o');appSha256=(Get-FileHash -LiteralPath $app -Algorithm SHA256).Hash.ToLower();driverSha256=(Get-FileHash -LiteralPath $driver -Algorithm SHA256).Hash.ToLower();transport='Public HTTPS/WSS through Cloudflare; no adb reverse';scope='Dedicated API30 emulator; no physical-phone performance claim'} | ConvertTo-Json | Set-Content -LiteralPath (Join-Path $evidence 'validation.json')
Write-Output $transcript
if (!$passed) { throw 'Internet beta native acceptance failed' }
