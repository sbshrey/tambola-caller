# Private Wi-Fi host

Installed 26 September 2026 at `C:\Users\sbshr\AppData\Local\TambolaTogetherHost`.

Current installed source is `b6d5eb6f7cc9c6c70006e4d9c422c2abe75187e0`, upgraded on 27 September (primary schema **007**, journal schema **003**, room protocol **4**). It includes indexed open lobbies, shorter matchmaking transactions, fresh recovery-journal authorization and skipped redundant live snapshots. The 51-JAR runtime is `f2b39e5bb622b0bd9c368da654a9412fc3e34d617e171497a1f521b7ec80ee10`. The upgrade verified an idle host, retained both database archives and old libraries, applied owner migrations/grants and started a fresh restricted service. Verified HTTPS purchase/refund/session checks passed. [Current deployment evidence](../full-game/reviews/server-wifi-2026-09-27/README.md) records acceptance and limits; alpha21 is the current compatible APK. Its complete 87-call emulator journey passed payout, process recovery and purchase/refund after fixing a client cancellation race.

The previous alpha17 service was source `9fe335d47b5b76688c0a148f64e074af4630fbdf` (primary schema 006). Its earlier session migration and Android HTTPS/WSS acceptance remain in [alpha17 evidence](../full-game/reviews/session-wifi-alpha17-2026-09-27/README.md).

The alpha18 APK previously passed a complete native round against alpha17. Its personal-results and timer changes required no host upgrade or interruption; see [alpha18 acceptance](../full-game/reviews/coin-ui-2026-09-27/README.md).

- Game endpoint: `https://192.168.1.4:8443` (PC Ethernet address, same LAN as Wi-Fi).
- Backend binds only `127.0.0.1:18080`; PostgreSQL binds only `127.0.0.1:55433`.
- PostgreSQL 16.15 uses separate `tambola_local` and `tambola_local_journal` databases, migration-owner and restricted runtime roles. Existing test database on 55432 is untouched.
- Caddy 2.11.4 terminates TLS and blocks `/internal/*` from the LAN. Its private CA is trusted only by the explicit `lan` and optimized `lanRelease` Wi-Fi APKs for this IP. No system certificate store was changed; ordinary release trust remains unchanged.
- Pinned Java 17 and PowerShell 7.6 runtimes, service libraries and configuration live outside the checkout. User/SYSTEM ACLs and Windows DPAPI protect configuration. Never copy `.secrets`, database files, private TLS keys or raw backups into Git/APKs.
- Hidden watcher restarts the worker; the worker restarts Java/Caddy and recovers the dedicated database. PID, executable and start-time checks protect unrelated processes.
- Current-user login startup is installed. It runs after this user signs in, **not before Windows login**. Do not describe it as a SYSTEM service or a reboot-tested deployment. Existing AC sleep timeout is already disabled; no power settings were changed.
- Backups run every 24 hours while the worker is active, retaining seven complete pairs. Primary is dumped before the journal. Archive-list validation passed; actual restoration of these installed-host backups has not yet been rehearsed. Keep the live recovery journal and replay newer deletion/logout intents during any future restore.

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

## Service upgrades

`full-game/tools/upgrade-windows-host.ps1 -SourceCommit <full-commit>` performs a read-only preflight of a clean committed checkout, matching `server:installDist` output and the expected private host. It refuses unfinished lobbies/rounds. Add `-Apply` to deploy the reviewed candidate.

The tool retains the previous libraries/configuration, stops serving, checks that no game arrived during preflight, makes and verifies a fresh primary/journal backup pair, then runs the candidate's migrations with separate owner credentials and reapplies restricted runtime grants. It points the host at versioned, checksum-verified libraries and requires a fresh healthy server process. Protected upgrade records and a retained copy of the backup pair stay under the host's `upgrades` directory; they are not exported to Git or the APK.

Before migration starts, a failed upgrade resumes the old service. Once migration has been attempted, it does **not** automatically restore a database or downgrade binaries: the old service may reject the new migration registry. Preserve the live deletion journal and investigate the protected logs before any recovery. Fresh backend readiness must still be followed by verified TLS transactions and native APK testing.

## Evidence boundary

`tools/verify-wifi-host.mjs` used two temporary authenticated profiles over verified HTTPS, completed 90 manual calls, claimed one chosen ticket/prize, checked own-ticket privacy and replayed the exact claim receipt after Java, Caddy, worker and database recovery. It deleted its QA profiles afterwards. It deliberately interrupts only the installed host; run it when no players are active.

`LanTransportTest` exercised the actual Android trust manager and app HTTP client over HTTPS/WSS to the LAN address, with cleartext rejected. It passed again on the matching alpha16 LAN APK, on the dedicated emulator without adb reverse.

`tools/verify-coin-host.mjs` used two temporary profiles to purchase six and two tickets into one lobby, verify a 1,400-coin/seven-prize pool, retry exact purchase/refund receipts and restore each 1,500-coin balance. Profiles were deleted afterwards. `tools/verify-native-host-restart.ps1` only permits a Java fault probe during the expected four-player `LanCoinGameTest` QA round; it refuses other live rooms and checks process identity. Native round completion must be assessed together with its report, not inferred from backend readiness.

The [copied installed-backup rehearsal](../full-game/reviews/installed-recovery-2026-09-27/README.md) passed with the exact installed runtime and restricted database roles. It restored the retained primary plus a newer journal into isolated stores, checked wallet continuity and exercised logout/deletion and receipt replay. The live host stayed in place; original archives were preserved and temporary copies removed. The repeatable command is `full-game/tools/rehearse-windows-recovery.ps1`; see [the runbook](../full-game/server/BACKUP_RECOVERY.md#copied-installed-windows-backup-rehearsal).

Physical phone Wi-Fi, Internet reachability, reboot behavior, live disaster cutover and release signing remain separate acceptance checks. PC request timings are not a mobile-network latency guarantee.

The alpha16 LAN APK also passed a complete native coin round on the dedicated emulator: 86 calls, all eight selected prizes, 2,400 coins paid, conserved aggregate wallets, results, Play Again and cancellation/refund. The Java server recovered in 5,678 ms during that round and the app continued over WSS. See [the archived results and frame-time limits](../full-game/reviews/coin-wifi-alpha16-2026-09-26/README.md).

The exact alpha17 APK passed a fresh 84-call native round after the session/journal migration: automatic device enrollment, six disjoint owned tickets, all eight selected claims, 2,400 coins paid, 3,300 final balance, conserved aggregate wallets, results, another purchase and its cancellation/refund. No fault restart was injected in this run. Diagnostic emulator frame p95 was 50 ms; physical/optimized smoothness remains open. The installed APK matched the packaged checksum, temporary QA profiles were deleted, and the host remained freshly ready over verified TLS. See [alpha17 results](../full-game/reviews/session-wifi-alpha17-2026-09-27/README.md).

Dependency sources: [Caddy v2.11.4](https://github.com/caddyserver/caddy/releases/tag/v2.11.4), [EDB PostgreSQL binaries](https://www.enterprisedb.com/download-postgresql-binaries). Caddy matched its published checksum. PostgreSQL came from the official HTTPS download; its observed SHA-256 is recorded with the installation evidence, not represented as a publisher signature verification.
