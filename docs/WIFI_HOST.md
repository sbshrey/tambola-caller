# Private Wi-Fi host

Installed 26 September 2026 at `C:\Users\sbshr\AppData\Local\TambolaTogetherHost`.

- Game endpoint: `https://192.168.1.4:8443` (PC Ethernet address, same LAN as Wi-Fi).
- Backend binds only `127.0.0.1:18080`; PostgreSQL binds only `127.0.0.1:55433`.
- PostgreSQL 16.15 uses separate `tambola_local` and `tambola_local_journal` databases, migration-owner and restricted runtime roles. Existing test database on 55432 is untouched.
- Caddy 2.11.4 terminates TLS and blocks `/internal/*` from the LAN. Its private CA is trusted only by the separate `lan` APK for this IP. No system certificate store was changed; release trust remains unchanged.
- Pinned Java 17 and PowerShell 7.6 runtimes, service libraries and configuration live outside the checkout. User/SYSTEM ACLs and Windows DPAPI protect configuration. Never copy `.secrets`, database files, private TLS keys or raw backups into Git/APKs.
- Hidden watcher restarts the worker; the worker restarts Java/Caddy and recovers the dedicated database. PID, executable and start-time checks protect unrelated processes.
- Current-user login startup is installed. It runs after this user signs in, **not before Windows login**. Do not describe it as a SYSTEM service or a reboot-tested deployment. Existing AC sleep timeout is already disabled; no power settings were changed.
- Backups run every 24 hours while the worker is active, retaining seven complete pairs. Primary is dumped before the journal. Archive-list validation passed; actual restoration of these installed-host backups has not yet been rehearsed. Keep the live deletion journal and replay newer deletions during any future restore.

## One administrator action for phone testing

This Codex shell is not elevated and has not opened the Windows firewall. In **Administrator PowerShell**, run:

```powershell
& 'D:\Github\App Ideas\tambola-caller\full-game\tools\enable-wifi-firewall.ps1'
```

The script allows only Caddy TCP 8443 on the configured address, on the existing Private network profile, from the local subnet. It does not open database ports or forward the router. A physical phone on the same Wi-Fi must still confirm reachability; PC/emulator checks cannot establish router/AP isolation behavior.

Reserve `192.168.1.4` for this PC in the router before depending on it. If its address changes, the host configuration and Wi-Fi APK must be rebuilt together. Sleep, logout, shutdown, power loss and lost connectivity can interrupt service; boot-before-login/public hosting remain separate work.

## Operation

Run with the pinned runtime; no repository process needs to remain open:

```powershell
$hostRoot = Join-Path $env:LOCALAPPDATA 'TambolaTogetherHost'
& "$hostRoot\runtime\powershell\pwsh.exe" -File "$hostRoot\windows-host.ps1" -Action Status
```

Other actions: `Start`, `Stop`, `InstallStartup`, `RemoveStartup`. Stop shuts down game serving and its watcher; the private database stays running. Backup archives and process logs stay in the protected host directory. Stale `checkedAt` means the reported state is not a fresh health confirmation.

To build the dedicated debug-signed Wi-Fi candidate from `full-game`:

```powershell
./gradlew.bat :app:assembleLan '-PtambolaApiUrl=https://192.168.1.4:8443' "-PtambolaLanCa=$env:LOCALAPPDATA\TambolaTogetherHost\tls-data\caddy\pki\authorities\local\root.crt"
```

This variant is for Wi-Fi validation. It is not the release-signed public Store artifact.

## Evidence boundary

`tools/verify-wifi-host.mjs` used two temporary authenticated profiles over verified HTTPS, completed 90 manual calls, claimed one chosen ticket/prize, checked own-ticket privacy and replayed the exact claim receipt after Java, Caddy, worker and database recovery. It deleted its QA profiles afterwards. It deliberately interrupts only the installed host; run it when no players are active.

`LanTransportTest` exercised the actual Android trust manager and app HTTP client over HTTPS/WSS to the LAN address, with cleartext rejected. It ran on the dedicated emulator without adb reverse. Physical phone Wi-Fi, Internet reachability, reboot behavior, wallet/purchase integration and release signing are not established by these checks.

Dependency sources: [Caddy v2.11.4](https://github.com/caddyserver/caddy/releases/tag/v2.11.4), [EDB PostgreSQL binaries](https://www.enterprisedb.com/download-postgresql-binaries). Caddy matched its published checksum. PostgreSQL came from the official HTTPS download; its observed SHA-256 is recorded with the installation evidence, not represented as a publisher signature verification.
