# Read-only access to the installed host. All restore/mutation work uses port 55432.
param([string]$Directory = (Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost'))
$ErrorActionPreference = 'Stop'
$gameRoot = Split-Path $PSScriptRoot -Parent
$hostRoot = [IO.Path]::GetFullPath($Directory)
$runId = [guid]::NewGuid().ToString('N').Substring(0,16)
$copyRoot = Join-Path $hostRoot "recovery-drills/$runId"
$evidencePath = Join-Path $gameRoot ".test-workspace/installed-recovery-$runId.json"
$runtime = @{}
$keys = @('TAMBOLA_RECOVERY_DIRECTORY','TAMBOLA_RECOVERY_EVIDENCE','TAMBOLA_RECOVERY_HOST','TAMBOLA_RECOVERY_RUN_ID',
    'TAMBOLA_TEST_DATABASE_PASSWORD')
$previous = @{}
foreach ($key in $keys) { $previous[$key] = [Environment]::GetEnvironmentVariable($key,'Process') }
function Within-Host([string]$Path) {
    $resolved = [IO.Path]::GetFullPath($Path)
    if (!$resolved.StartsWith($hostRoot+'\',[StringComparison]::OrdinalIgnoreCase)) { throw 'Path outside installed host' }
    return $resolved
}
try {
    $config = Get-Content -LiteralPath (Join-Path $hostRoot 'host.json') -Raw | ConvertFrom-Json
    $upgrade = Within-Host (Split-Path $config.serviceLib -Parent)
    $deployment = Get-Content -LiteralPath (Join-Path $upgrade 'deployment.json') -Raw | ConvertFrom-Json
    if ($deployment.sourceCommit -ne $config.sourceCommit -or $deployment.backupPair -notmatch '^\d{8}-\d{6}\.json$') { throw 'Unexpected deployment manifest' }
    $manifest = Get-Content -LiteralPath (Join-Path $upgrade "backups/$($deployment.backupPair)") -Raw | ConvertFrom-Json
    $primary = @($manifest.files | Where-Object name -Match '^\d{8}-\d{6}-primary\.dump$')
    if ($primary.Count -ne 1) { throw 'Expected one retained primary archive' }
    $original = Within-Host (Join-Path $upgrade "backups/$($primary[0].name)")
    if ((Get-FileHash -LiteralPath $original -Algorithm SHA256).Hash.ToLowerInvariant() -ne $primary[0].sha256 -or
        (Get-Item -LiteralPath $original).Length -ne $primary[0].bytes) { throw 'Retained archive changed' }
    New-Item -ItemType Directory -Path (Within-Host $copyRoot) | Out-Null
    # Sensitive copies inherit only explicit current-user, SYSTEM and administrators access.
    $acl = Get-Acl -LiteralPath $copyRoot
    $acl.SetAccessRuleProtection($true,$false)
    foreach ($sid in @([Security.Principal.WindowsIdentity]::GetCurrent().User.Value,'S-1-5-18','S-1-5-32-544')) {
        $rule = [Security.AccessControl.FileSystemAccessRule]::new([Security.Principal.SecurityIdentifier]::new($sid),
            'FullControl','ContainerInherit,ObjectInherit','None','Allow')
        $acl.AddAccessRule($rule)
    }
    Set-Acl -LiteralPath $copyRoot -AclObject $acl
    Copy-Item -LiteralPath $original -Destination (Join-Path $copyRoot 'primary.dump')
    $protected = (Get-Content -LiteralPath (Join-Path $hostRoot 'runtime.secrets') -Raw).Trim() | ConvertTo-SecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($protected)
    try { $runtime = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) | ConvertFrom-Json -AsHashtable }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $protected.Dispose() }
    if ($runtime.TAMBOLA_DELETION_DATABASE_URL -ne 'jdbc:postgresql://127.0.0.1:55433/tambola_local_journal' -or
        $runtime.TAMBOLA_DELETION_DATABASE_USER -ne 'tambola_journal_app') { throw 'Unexpected installed journal identity' }
    $dump = [Diagnostics.ProcessStartInfo]::new()
    $dump.FileName = Within-Host (Join-Path $config.postgresBin 'pg_dump.exe')
    $dump.UseShellExecute = $false; $dump.CreateNoWindow = $true
    $dump.RedirectStandardOutput = $true; $dump.RedirectStandardError = $true
    foreach ($arg in @('--no-password','-h','127.0.0.1','-p','55433','-U',$runtime.TAMBOLA_DELETION_DATABASE_USER,
        '-d','tambola_local_journal','--format=custom','--no-owner','--no-privileges',"--file=$(Join-Path $copyRoot 'journal.dump')")) { $dump.ArgumentList.Add($arg) }
    $dump.Environment['PGPASSWORD'] = $runtime.TAMBOLA_DELETION_DATABASE_PASSWORD
    $process = [Diagnostics.Process]::Start($dump)
    $stdout = $process.StandardOutput.ReadToEndAsync(); $stderr = $process.StandardError.ReadToEndAsync()
    if (!$process.WaitForExit(30000)) { $process.Kill(); throw 'Journal snapshot timed out' }
    $stdout.GetAwaiter().GetResult() | Out-Null; $stderr.GetAwaiter().GetResult() | Out-Null
    if ($process.ExitCode -ne 0) { throw 'Journal snapshot failed; private diagnostics omitted' }
    $process.Dispose(); $dump.Environment.Remove('PGPASSWORD') | Out-Null; $runtime.Clear()
    $env:TAMBOLA_RECOVERY_DIRECTORY = $copyRoot; $env:TAMBOLA_RECOVERY_HOST = $hostRoot
    $env:TAMBOLA_RECOVERY_EVIDENCE = $evidencePath; $env:TAMBOLA_RECOVERY_RUN_ID = $runId
    $env:TAMBOLA_TEST_DATABASE_PASSWORD = (Get-Content -LiteralPath (Join-Path $gameRoot '.test-workspace/postgres-password.txt') -Raw).Trim()
    & node (Join-Path $PSScriptRoot 'installed-recovery.mjs')
    if ($LASTEXITCODE -ne 0) { throw 'Recovery drill did not pass; see sanitized evidence' }
} finally {
    $runtime.Clear()
    foreach ($key in $keys) { [Environment]::SetEnvironmentVariable($key,$previous[$key],'Process') }
    $copiesRemoved = $false
    if (Test-Path -LiteralPath $copyRoot) {
        $checked = Within-Host $copyRoot
        if ((Split-Path $checked -Leaf) -ne $runId -or (Split-Path (Split-Path $checked -Parent) -Leaf) -ne 'recovery-drills') { throw 'Unexpected cleanup target' }
        Remove-Item -LiteralPath $checked -Recurse -Force
        $copiesRemoved = !(Test-Path -LiteralPath $checked)
    }
    if (Test-Path -LiteralPath $evidencePath) {
        $evidence = Get-Content -LiteralPath $evidencePath -Raw | ConvertFrom-Json -AsHashtable
        $evidence.sensitiveCopiesRemoved = $copiesRemoved
        $evidence.wrapperSha256 = (Get-FileHash -LiteralPath $PSCommandPath -Algorithm SHA256).Hash.ToLowerInvariant()
        $evidence | ConvertTo-Json -Depth 10 | Set-Content -LiteralPath $evidencePath -Encoding utf8
        Write-Output "Sanitized recovery evidence: $evidencePath"
    }
}
