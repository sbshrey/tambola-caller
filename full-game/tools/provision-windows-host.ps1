#requires -Version 7.0
param(
    [Parameter(Mandatory)][string]$LanAddress,
    [Parameter(Mandatory)][string]$PostgresDirectory,
    [Parameter(Mandatory)][string]$JavaHome,
    [Parameter(Mandatory)][string]$CaddyExecutable,
    [string]$Directory = (Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost')
)
$ErrorActionPreference = 'Stop'
$address = [Net.IPAddress]::Parse($LanAddress)
$octets = $address.GetAddressBytes()
if ($octets.Length -ne 4 -or !($octets[0] -eq 10 -or ($octets[0] -eq 172 -and $octets[1] -ge 16 -and $octets[1] -le 31) -or ($octets[0] -eq 192 -and $octets[1] -eq 168))) { throw 'Use a private IPv4 address' }
$hostDirectory = [IO.Path]::GetFullPath($Directory).TrimEnd('\')
$gameDirectory = Split-Path $PSScriptRoot
if (Test-Path -LiteralPath (Join-Path $hostDirectory 'host.json')) { throw 'Host already provisioned. Preserve its data and use a reviewed deployment upgrade.' }
New-Item -ItemType Directory -Path $hostDirectory -Force | Out-Null
$sid = [Security.Principal.WindowsIdentity]::GetCurrent().User.Value
& icacls.exe $hostDirectory /inheritance:r /grant:r "*$($sid):(OI)(CI)F" '*S-1-5-18:(OI)(CI)F' *> $null
if ($LASTEXITCODE -ne 0) { throw 'Could not protect host directory' }
foreach ($name in @('logs','backups','runtime','service','tls-data','tls-config')) { New-Item -ItemType Directory -Path (Join-Path $hostDirectory $name) -Force | Out-Null }

function Invoke-Checked([string]$executable, [string[]]$arguments, [string]$label) {
    if ([IO.Path]::GetFileName($executable) -eq 'pg_ctl.exe') {
        # PowerShell's native pipeline waits for inherited child handles. pg_ctl
        # deliberately leaves postgres running, so wait only for pg_ctl itself.
        $quoted = $arguments | ForEach-Object { '"' + ([regex]::Replace([regex]::Replace($_, '(\\*)"', '$1$1\"'), '(\\+)$', '$1$1')) + '"' }
        $process = Start-Process -FilePath $executable -WindowStyle Hidden -PassThru -ArgumentList $quoted -RedirectStandardOutput (Join-Path $hostDirectory "logs/provision-$label.log") -RedirectStandardError (Join-Path $hostDirectory "logs/provision-$label.err.log")
        if (!$process.WaitForExit(30000)) { throw "Provision step timed out: $label" }
        $code = $process.ExitCode; $process.Dispose()
        if ($code -ne 0) { throw "Provision step failed: $label" }
        return
    }
    & $executable @arguments *> (Join-Path $hostDirectory "logs/provision-$label.log")
    if ($LASTEXITCODE -ne 0) { throw "Provision step failed: $label. Detailed log is in the protected host directory." }
}
function Protect-Json($value, [string]$path) { ConvertTo-SecureString ($value | ConvertTo-Json -Compress) -AsPlainText -Force | ConvertFrom-SecureString | Set-Content -LiteralPath $path }
function Read-Protected([string]$path) {
    $value = (Get-Content -LiteralPath $path -Raw).Trim() | ConvertTo-SecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($value)
    try { return [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) | ConvertFrom-Json -AsHashtable }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $value.Dispose() }
}
$credentialPath = Join-Path $hostDirectory 'provision.secrets'
if (!(Test-Path -LiteralPath $credentialPath)) {
    $keys = @{}
    foreach ($name in @('owner','mainOwner','journalOwner','mainApp','journalApp','metrics')) {
        $keys[$name] = [Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(32))
    }
    Protect-Json $keys $credentialPath
}
$keys = Read-Protected $credentialPath
$keys.metrics = $keys.metrics.TrimEnd('=').Replace('+', '-').Replace('/', '_')
$pg = Join-Path $hostDirectory 'postgres'
New-Item -ItemType Directory -Path $pg -Force | Out-Null
foreach ($name in @('bin','lib','share')) {
    if (!(Test-Path -LiteralPath (Join-Path $pg $name))) { Copy-Item -LiteralPath (Join-Path $PostgresDirectory $name) -Destination $pg -Recurse }
}
$bin = Join-Path $pg 'bin'
$data = Join-Path $hostDirectory 'data'
$pwFile = Join-Path $hostDirectory 'init.password'
if (!(Test-Path -LiteralPath (Join-Path $data 'PG_VERSION'))) {
    try {
        [IO.File]::WriteAllText($pwFile, $keys.owner)
        Invoke-Checked (Join-Path $bin 'initdb.exe') @('-D',$data,'-U','tambola_owner','--pwfile',$pwFile,'--auth-host=scram-sha-256','--auth-local=scram-sha-256','--encoding=UTF8','--locale=C') 'initdb'
    } finally { if (Test-Path -LiteralPath $pwFile) { Remove-Item -LiteralPath $pwFile } }
    @("listen_addresses = '127.0.0.1'",'port = 55433','max_connections = 40',"shared_buffers = '64MB'",'password_encryption = scram-sha-256') | Add-Content -LiteralPath (Join-Path $data 'postgresql.conf')
}
$pgctl = Join-Path $bin 'pg_ctl.exe'
& $pgctl -D $data status *> $null
if ($LASTEXITCODE -ne 0) { Invoke-Checked $pgctl @('-D',$data,'-l',(Join-Path $hostDirectory 'logs/postgres.log'),'-w','start') 'postgres-start' }
$psql = Join-Path $bin 'psql.exe'
$baseArgs = @('-h','127.0.0.1','-p','55433','-U','tambola_owner','-v','ON_ERROR_STOP=1')
$env:PGPASSWORD = $keys.owner
$sqlPath = Join-Path $hostDirectory 'roles.pending.sql'
try {
    $roleKeys = @{tambola_main_owner='mainOwner';tambola_journal_owner='journalOwner';tambola_main_app='mainApp';tambola_journal_app='journalApp'}
    $sql = foreach ($role in $roleKeys.Keys) {
        "SELECT 'CREATE ROLE $role LOGIN PASSWORD ''$($keys[$roleKeys[$role]])''' WHERE NOT EXISTS (SELECT FROM pg_roles WHERE rolname = '$role') \gexec"
    }
    [IO.File]::WriteAllText($sqlPath, ($sql -join "`n"))
    Invoke-Checked $psql ($baseArgs + @('-d','postgres','-f',$sqlPath)) 'roles'
    foreach ($database in @('tambola_local','tambola_local_journal')) {
        $exists = & $psql @baseArgs -d postgres -At -c "SELECT 1 FROM pg_database WHERE datname = '$database'"
        if ($LASTEXITCODE -ne 0) { throw 'Database inventory failed' }
        if ($exists -ne '1') {
            $owner = if ($database -eq 'tambola_local') {'tambola_main_owner'} else {'tambola_journal_owner'}
            Invoke-Checked $psql ($baseArgs + @('-d','postgres','-c',"CREATE DATABASE $database OWNER $owner")) "create-$database"
        }
    }
} finally { Remove-Item Env:PGPASSWORD; if (Test-Path -LiteralPath $sqlPath) { Remove-Item -LiteralPath $sqlPath } }
$jre = Join-Path $hostDirectory 'runtime/java'
if (!(Test-Path -LiteralPath $jre)) { Invoke-Checked (Join-Path $JavaHome 'bin/jlink.exe') @('--add-modules','java.se,jdk.crypto.ec,jdk.unsupported,jdk.management,jdk.zipfs,jdk.jfr','--strip-debug','--no-header-files','--no-man-pages','--output',$jre) 'java-runtime' }
$serviceLib = Join-Path $hostDirectory 'service/lib'
if (!(Test-Path -LiteralPath $serviceLib)) { Copy-Item -LiteralPath (Join-Path $gameDirectory 'server/build/install/server/lib') -Destination (Join-Path $hostDirectory 'service') -Recurse }
$java = Join-Path $jre 'bin/java.exe'
$runtime = @{
    TAMBOLA_BIND_HOST='127.0.0.1'; PORT='18080';
    TAMBOLA_DATABASE_URL='jdbc:postgresql://127.0.0.1:55433/tambola_local'; TAMBOLA_DATABASE_USER='tambola_main_owner'; TAMBOLA_DATABASE_PASSWORD=$keys.mainOwner;
    TAMBOLA_DELETION_DATABASE_URL='jdbc:postgresql://127.0.0.1:55433/tambola_local_journal'; TAMBOLA_DELETION_DATABASE_USER='tambola_journal_owner'; TAMBOLA_DELETION_DATABASE_PASSWORD=$keys.journalOwner;
    TAMBOLA_PUBLIC_ORIGIN="https://$($LanAddress):8443"; TAMBOLA_METRICS_TOKEN=$keys.metrics
}
try {
    foreach ($key in $runtime.Keys) { [Environment]::SetEnvironmentVariable($key, $runtime[$key], 'Process') }
    Invoke-Checked $java @('-cp',(Join-Path $serviceLib '*'),'io.github.sbshrey.tambola.server.ServerKt','--migrate') 'migrations'
} finally { foreach ($key in $runtime.Keys) { [Environment]::SetEnvironmentVariable($key, $null, 'Process') } }
foreach ($kind in @('main','journal')) {
    $database = if ($kind -eq 'main') {'tambola_local'} else {'tambola_local_journal'}
    $env:PGPASSWORD = $keys["$($kind)Owner"]
    try {
        Invoke-Checked $psql @('-h','127.0.0.1','-p','55433','-U',"tambola_$($kind)_owner",'-d',$database,'-v','ON_ERROR_STOP=1','-v',"database=$database",'-v',"runtime_role=tambola_$($kind)_app",'-f',(Join-Path $gameDirectory "server/src/main/resources/db/permissions/$(if ($kind -eq 'main') {'primary'} else {'journal'}).sql")) "$kind-grants"
    } finally { Remove-Item Env:PGPASSWORD }
}
$runtime.TAMBOLA_DATABASE_USER='tambola_main_app'; $runtime.TAMBOLA_DATABASE_PASSWORD=$keys.mainApp
$runtime.TAMBOLA_DELETION_DATABASE_USER='tambola_journal_app'; $runtime.TAMBOLA_DELETION_DATABASE_PASSWORD=$keys.journalApp
Protect-Json $runtime (Join-Path $hostDirectory 'runtime.secrets')
$caddy = Join-Path $hostDirectory 'runtime/caddy.exe'
Copy-Item -LiteralPath $CaddyExecutable -Destination $caddy
$caddyFile = Join-Path $hostDirectory 'Caddyfile'
@"
{
    admin off
    auto_https disable_redirects
    skip_install_trust
}
https://$($LanAddress):8443 {
    bind $LanAddress
    tls internal
    @internal path /internal/*
    respond @internal 404
    reverse_proxy 127.0.0.1:18080
}
"@ | Set-Content -LiteralPath $caddyFile
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'windows-host.ps1') -Destination (Join-Path $hostDirectory 'windows-host.ps1')
Copy-Item -LiteralPath (Join-Path $PSScriptRoot 'windows-backup.ps1') -Destination (Join-Path $hostDirectory 'windows-backup.ps1')
@{java=$java; postgresBin=$bin;postgresData=$data;postgresPort=55433;serverPort=18080;serviceLib=$serviceLib;caddy=$caddy;caddyFile=$caddyFile;origin=$runtime.TAMBOLA_PUBLIC_ORIGIN} |
    ConvertTo-Json | Set-Content -LiteralPath (Join-Path $hostDirectory 'host.json')
$keys.Clear(); $runtime.Clear()
$powerShellRuntime = Join-Path $hostDirectory 'runtime/powershell'
if (!(Test-Path -LiteralPath (Join-Path $powerShellRuntime 'pwsh.exe'))) { Copy-Item -LiteralPath $PSHOME -Destination $powerShellRuntime -Recurse }
Write-Output 'Provisioned separate protected Wi-Fi host. Start it with windows-host.ps1 -Action Start. Firewall and APK trust still require validation.'
