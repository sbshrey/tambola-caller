param(
    [Parameter(Mandatory)][ValidatePattern('^[a-z][a-z0-9-]{4,28}[a-z0-9]$')][string]$ProjectId,
    [switch]$CreateProject
)
$ErrorActionPreference = 'Stop'
$firebase = (Get-Command firebase.cmd -ErrorAction Stop).Source
$projectRoot = Split-Path $PSScriptRoot -Parent
function Invoke-FirebaseJson([string[]]$Arguments) {
    # Never call login:list here: its JSON contains account tokens.
    $captured = & $firebase @Arguments --json --non-interactive 2>$null
    $exit = $LASTEXITCODE
    try { $response = ($captured -join "`n") | ConvertFrom-Json } catch { throw 'Firebase returned an unreadable response. Sign in using firebase login --reauth.' }
    if ($exit -ne 0 -or $response.status -ne 'success') { throw 'Firebase setup failed. Check the selected project and run firebase login --reauth. Raw CLI output is intentionally withheld.' }
    return $response.result
}
Push-Location $projectRoot
try {
    $projects = Invoke-FirebaseJson -Arguments @('projects:list')
    if (-not (@($projects) | Where-Object projectId -eq $ProjectId)) {
        if (-not $CreateProject) { throw 'Project is not available. Use -CreateProject for a new free Firebase project.' }
        $null = Invoke-FirebaseJson -Arguments @('projects:create', $ProjectId, '--display-name', 'Tambola Together Beta')
    }
    $apps = Invoke-FirebaseJson -Arguments @('apps:list', 'ANDROID', '--project', $ProjectId)
    $app = @($apps) | Where-Object packageName -eq 'io.github.sbshrey.tambola.game.beta' | Select-Object -First 1
    if (-not $app) {
        $app = Invoke-FirebaseJson -Arguments @('apps:create', 'ANDROID', 'Tambola Together Beta', '--package-name', 'io.github.sbshrey.tambola.game.beta', '--project', $ProjectId)
    }
    $target = Join-Path $projectRoot 'app/src/publicBeta/google-services.json'
    $staging = Join-Path $projectRoot ('.test-workspace/firebase-' + [guid]::NewGuid().ToString('N') + '.json')
    $null = New-Item -ItemType Directory -Path (Split-Path $staging) -Force
    $null = Invoke-FirebaseJson -Arguments @('apps:sdkconfig', 'ANDROID', $app.appId, '--project', $ProjectId, '--out', $staging)
    $configuration = Get-Content -LiteralPath $staging -Raw | ConvertFrom-Json
    if ($configuration.project_info.project_id -ne $ProjectId) { throw 'Downloaded configuration has the wrong project ID.' }
    if ('io.github.sbshrey.tambola.game.beta' -notin @($configuration.client.client_info.android_client_info.package_name)) { throw 'Downloaded configuration has the wrong Android package.' }
    Move-Item -LiteralPath $staging -Destination $target -Force
    Write-Output "Firebase Android configuration saved. Build publicBeta with -PtambolaFirebase=true."
    Write-Output 'No billing account, paid services, database, or Cloud Functions were enabled. Console crash/trace verification remains required.'
} finally { Pop-Location }
