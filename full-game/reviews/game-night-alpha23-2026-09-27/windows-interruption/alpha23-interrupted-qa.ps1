param([ValidateSet('inspect','apply')][string]$Mode = 'inspect')
$ErrorActionPreference = 'Stop'
$hostRoot23 = Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost'
$config23 = Get-Content -LiteralPath (Join-Path $hostRoot23 'host.json') -Raw | ConvertFrom-Json
$protected23 = (Get-Content -LiteralPath (Join-Path $hostRoot23 'runtime.secrets') -Raw).Trim() | ConvertTo-SecureString
$pointer23 = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($protected23)
try { $runtime23 = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer23) | ConvertFrom-Json -AsHashtable }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer23); $protected23.Dispose() }
$info23 = [Diagnostics.ProcessStartInfo]::new($config23.java)
$info23.UseShellExecute = $false; $info23.CreateNoWindow = $true; $info23.WindowStyle = 'Hidden'
$info23.RedirectStandardOutput = $true; $info23.RedirectStandardError = $true
foreach ($arg23 in @('-cp',"$PSScriptRoot;$($config23.serviceLib)/*",'Alpha23InterruptedQa',$Mode)) { $info23.ArgumentList.Add($arg23) }
foreach ($key23 in $runtime23.Keys) { $info23.Environment[$key23] = [string]$runtime23[$key23] }
$process23 = [Diagnostics.Process]::new(); $process23.StartInfo = $info23
try {
  if (!$process23.Start()) { throw 'QA cleanup process did not start' }
  $output23 = $process23.StandardOutput.ReadToEndAsync(); $error23 = $process23.StandardError.ReadToEndAsync()
  if (!$process23.WaitForExit(30000)) { $process23.Kill($true); throw 'QA cleanup timed out' }
  $output23.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $PSScriptRoot "alpha23-interrupted-qa-$Mode.txt")
  $error23.GetAwaiter().GetResult() | Set-Content -LiteralPath (Join-Path $PSScriptRoot "alpha23-interrupted-qa-$Mode-err.txt")
  if ($process23.ExitCode -ne 0) { throw 'QA cleanup failed; inspect the local diagnostic' }
  Get-Content -LiteralPath (Join-Path $PSScriptRoot "alpha23-interrupted-qa-$Mode.txt")
} finally { $process23.Dispose(); $runtime23 = $null }
