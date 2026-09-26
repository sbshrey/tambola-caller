# Alpha16 Wi-Fi coin-game acceptance

This archive identifies the debug-signed LAN APK in `candidate.json` and the actual installed server at commit `0b4c8de948d4925975d4e772e4b8ac1b458d65ec`. The APK includes the claim-picker correction committed with this archive: later houses wait until the previous house's call window closes, and an earlier winning ticket cannot claim another house. Locked, unwon prizes no longer receive a misleading checkmark.

## Deployment and transport

- The idle installed host was upgraded from schema 003/protocol 3 to schema 005/protocol 4. The guarded tool retained previous configuration/libraries, made a fresh primary/journal backup pair, verified archive hashes, applied owner migrations/grants, and required a fresh ready process using restricted runtime credentials. Protected raw backups, credentials, configuration and private certificates remain outside the repository.
- `deployment.json` records the instant the new backend became ready. Its TLS transaction field deliberately still says pending; the later `coin-host-smoke.json` records the subsequent successful verified HTTPS transactions.
- The smoke used two authenticated QA profiles, six and two purchased tickets, a 1,400-coin pool/seven prizes, exact duplicate purchase receipts, and exact cancellation/refund receipts including the final closed lobby. Both balances returned to 1,500. Temporary profiles were deleted.
- `LanTransportTest` passed on the installed LAN APK using Android's real trust manager and HTTPS/WSS, without adb reverse. It checked own-ticket privacy, explicit computer identities and blocked cleartext. This establishes emulator-to-LAN transport, not physical phone Wi-Fi reachability.
- Build, nine app unit tests and Android lint passed. `apksigner` verified the APK's v2 signature with the existing Android Debug certificate. It is not a Store-signed release.
- Both `CoinGameTest` cases passed again against the isolated loopback fixture after rebuilding debug/test APKs: the updated picker explicitly checks that House Two is initially disabled, and the lost-purchase-response/activity-recreation test still retries without a duplicate debit. The fixture stopped its owned processes and removed its adb mappings. The exact packaged LAN APK was then reinstalled on the emulator.

## Complete native round

`LanCoinGameTest` passed in 469.579 seconds. It bought six tickets through the app, created three passive authenticated QA peers with six tickets each, and used normal twelve-second lobby/five-second calls. Four real QA identities funded 2,400 coins and eight prizes; this deterministic claim acceptance scenario had no computer opponents. Other tests cover computer participation.

The native player manually marked all called numbers on the appropriate owned ticket pages and selected each of the eight prizes through its ticket's picker. The round finished at call 86. All eight awards belonged to that player, settled winnings were 2,400 with no unawarded refund, and the wallet reached 3,300. The four wallets still summed to their original combined 6,000 coins. The app displayed results, bought three tickets into a different round (balance 3,000), and cancelled for an exact refund (balance 3,300). Finally all four QA profiles were deleted and original preferences/system animation settings restored. The final report's `calls: 0`/`claims: 0` reflect the subsequently cancelled lobby; `finishedCalls: 86` identifies the completed round.

`coin-native-host-restart.json` records a Java-server interruption at call 20, after verifying that this was the host's only unfinished game and checking PID, executable, command and start time. The supervisor produced a fresh ready server in 5,678 ms. The native completion report must separately establish continued game state and settlement. This probe is not a Windows reboot, phone disconnect or public network test.

## Limits and next gates

The PC/emulator HTTP timings are not a mobile-network or concurrent-room latency target. The complete round collected 5,388 actual window frames with system animator scale enabled, excluding seven first-draw frames and losing no reports. The p95 bucket upper bound was 51 ms, the maximum 236.84 ms, and 3,629 frames exceeded 16.67 ms. **This does not pass a 60-fps smoothness target.** It is a debuggable emulator run with automated tapping/paging, not an optimized physical-device benchmark; profiling and a properly measured improvement remain necessary.

Remaining release work includes durable guest identity refresh/recovery (the current session expires after seven days), native free-refill acceptance, process-death recovery, coin-specific deletion/backup-restore acceptance, large-text/Hindi/compact layouts, optimized-build/physical-device frame measurements and concurrent-room latency. Physical phone reachability, the administrator firewall step, actual reboot behavior, public TLS and production signing remain separate gates. The PC host runs after this Windows user signs in and cannot serve through shutdown or lost connectivity.

All pictured players and game data are temporary QA identities. No tokens, keys, other players' hands, hidden draw order or raw database archives are included. `SHA256SUMS` covers each archived file except itself.
