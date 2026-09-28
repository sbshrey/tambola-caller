#requires -Version 7.0
param([ValidateSet('Prepare','Check','Delete')][string]$Action = 'Check')
$ErrorActionPreference = 'Stop'
$recordPath = Join-Path (Split-Path $PSScriptRoot -Parent) '.test-workspace/admob-ssv-probe.secrets'
$record = $null
function Request([string]$Path, [string]$Method = 'GET', $Body = $null, $Actor = $null) {
    $arguments = @{ Uri = $record.origin + $Path; Method = $Method; TimeoutSec = 15; MaximumRedirection = 0 }
    if ($Actor) { $arguments.Headers = @{ Authorization = 'Bearer ' + $Actor.token } }
    if ($null -ne $Body) { $arguments.Body = $Body | ConvertTo-Json -Compress; $arguments.ContentType = 'application/json' }
    try { return Invoke-RestMethod @arguments } catch { throw "Reward verification request failed: $Method $Path. Response withheld." }
}
function Save-Record {
    $secure = ($record | ConvertTo-Json -Depth 8 -Compress) | ConvertTo-SecureString -AsPlainText -Force
    try { [IO.File]::WriteAllText($recordPath, (ConvertFrom-SecureString $secure)) } finally { $secure.Dispose() }
}
if ($Action -eq 'Prepare') {
    if (Test-Path -LiteralPath $recordPath) { throw 'A saved probe already exists. Check or delete it before preparing another.' }
    $directory = Invoke-RestMethod ('https://raw.githubusercontent.com/sbshrey/tambola-caller/codex/public-beta-channel/server.json?minute=' + [Math]::Floor([DateTimeOffset]::UtcNow.ToUnixTimeSeconds()/60)) -TimeoutSec 15 -MaximumRedirection 0
    if ($directory.service -ne 'tambola-together-public-beta-v1' -or $directory.origin -notmatch '^https://[a-z0-9]+(?:-[a-z0-9]+)*\.trycloudflare\.com$' -or $directory.expiresAt -le [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds()) { throw 'Invalid or expired public directory.' }
    $record = @{origin=$directory.origin}
    $record.actor = Request '/v1/guests' 'POST' @{displayName='Ad callback verification'}
    Save-Record # Persist cleanup capability before any later request can fail.
    $wallet = Request '/v1/wallet/login-rewards' 'POST' $null $record.actor
    $record.before = $wallet.wallet.balance
    $record.intent = Request '/v1/wallet/ad-intents' 'POST' $null $record.actor
    if ($record.intent.adUnit -ne 'ca-app-pub-1312548197553464/9960291000' -or $record.intent.coins -ne 1000) { throw 'Unexpected server reward configuration.' }
    Save-Record
    [pscustomobject]@{callback=$record.origin+'/admob/reward';customData=$record.intent.id;rewardAmount=1000;rewardItem='coins';expiresAt=[DateTimeOffset]::FromUnixTimeMilliseconds($record.intent.expiresAt).ToString('o')}
    Write-Output 'Use this custom data in the AdMob SSV test twice, then run -Action Check. No ad impression or click is needed.'
    exit
}
if (!(Test-Path -LiteralPath $recordPath)) { throw 'No saved callback verification exists. Run -Action Prepare first.' }
$secure = (Get-Content -LiteralPath $recordPath -Raw).Trim() | ConvertTo-SecureString
$pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $record = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) | ConvertFrom-Json -AsHashtable }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secure.Dispose() }
if ($Action -eq 'Delete') {
    $null = Request '/v1/guests/me/delete' 'POST' @{id=[guid]::NewGuid().ToString()} $record.actor
    Remove-Item -LiteralPath $recordPath
    Write-Output 'The owned callback-test profile and its reward records were deleted.'
    exit
}
if (!$record.intent) { throw 'Preparation did not finish. Delete this owned probe before retrying.' }
$status = Request ('/v1/wallet/ad-intents/' + $record.intent.id) 'GET' $null $record.actor
$delta = $status.wallet.balance - $record.before
if ($status.confirmed -and $delta -ne 1000) { throw 'Confirmed reward has an unexpected wallet delta.' }
[pscustomobject]@{confirmed=$status.confirmed;coinsAdded=$delta;expectedCoins=1000;customData=$record.intent.id}
