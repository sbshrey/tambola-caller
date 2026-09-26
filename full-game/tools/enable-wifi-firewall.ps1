# Run once in an Administrator PowerShell. Restricts the installed proxy to the
# configured private address and local subnet; opens no database/public ports.
param([string]$Directory = (Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost'))
$ErrorActionPreference = 'Stop'
$principal = New-Object Security.Principal.WindowsPrincipal([Security.Principal.WindowsIdentity]::GetCurrent())
if (!$principal.IsInRole([Security.Principal.WindowsBuiltInRole]::Administrator)) { throw 'Run this script in Administrator PowerShell to create the Windows firewall rule.' }
$root = [IO.Path]::GetFullPath($Directory)
$config = Get-Content -LiteralPath (Join-Path $root 'host.json') -Raw | ConvertFrom-Json
$uri = [Uri]$config.origin
if ($uri.Scheme -ne 'https' -or $uri.Port -ne 8443) { throw 'Unexpected LAN endpoint' }
$address = Get-NetIPAddress -AddressFamily IPv4 -IPAddress $uri.Host
$profile = Get-NetConnectionProfile -InterfaceIndex $address.InterfaceIndex
if ($profile.NetworkCategory -ne 'Private') { throw 'The configured interface must already be a trusted Private network' }
$program = [IO.Path]::GetFullPath($config.caddy)
if ($program -ne [IO.Path]::GetFullPath((Join-Path $root 'runtime/caddy.exe')) -or !(Test-Path -LiteralPath $program)) { throw 'Unexpected proxy executable' }
$name = 'TambolaTogether-PrivateWifi-8443'
$existing = Get-NetFirewallRule -Name $name -ErrorAction SilentlyContinue
if ($existing) { $existing | Remove-NetFirewallRule }
New-NetFirewallRule -Name $name -DisplayName 'Tambola Together - private Wi-Fi' -Direction Inbound -Action Allow -Enabled True -Profile Private -Protocol TCP -LocalPort 8443 -LocalAddress $uri.Host -RemoteAddress LocalSubnet -Program $program | Out-Null
Write-Output 'Allowed Tambola HTTPS on the configured private address for the local subnet only.'
