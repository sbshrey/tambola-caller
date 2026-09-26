# Alpha16 coin matchmaking integration checkpoint

26 September 2026, branch `shrey/full-tambola-game`. This checkpoint implements the online-only coin flow. The installed Wi-Fi service has not yet been upgraded; do not distribute this loopback debug APK as a LAN release.

## Delivered behavior

- One entry screen: 1–6 tickets, three selected by default, 100 free virtual coins per ticket, one Play action. First play creates and securely saves a guest identity without a registration form. Starter balance is 1,500; the server permits a bounded 500-coin refill below one ticket's balance.
- One transaction selects a real-player lobby, reserves the selected quantity, charges the wallet and records the retry receipt. A 12-second server countdown replaces Ready/Start. Up to eight human seats; explicitly labelled computers fill to four participants. Computers buy three virtual tickets each.
- At closure the server freezes independent ticket counts and the prize pool. Five small prizes receive 10% each; the remainder funds one, two or three houses according to total tickets. The resulting six-, seven- or eight-slot schedule is immutable during play.
- Automatic five-second calls, manual dabs, one/two readable tickets per page, up/down arrows and a Claim button beside each card. The picker shows coin values and submits only the chosen ticket/prize. Claims can recover a competing revision while the same call is still open; they cannot move to another call or round.
- Prize receipts are provisional until their call closes. Payout/refund ledger keys prevent duplicate credits. Unclaimed funds return proportionally at completion/cancellation. Leaving an unstarted table or an interrupted countdown refunds the purchase once.
- Highest wallet revision is retained independently of room revision. Lost purchase replies retain the original operation and chosen quantity across activity recreation; retry recovers the same table and charge.

## Verification actually performed

189 distinct JVM tests passed across the full baseline and focused extensions: domain 50, client 23, server 107, app 9. `jvm-baseline-summary.json` records the full baseline (103 server tests); `focused-summary.json` and `coin-refinement-validation.txt` record the four added server cases plus relevant reruns. This is the union of those tests, not a claim that the final Gradle command executed all 189 at once.

Coverage includes concurrent purchases/duplicate IDs, room capacity, independent quantities and disjoint hands, insufficient-balance rollback, refunds/expiry, queued profile deletion, receipt redaction, restricted database roles, forged HTTP prices, all three houses, same-call ties, deterministic computer allocation and full-pool conservation. The server restart cases here recreate the service object over persistent PostgreSQL; they do not attest new-binary installed-host process recovery.

Android debug build, nine app unit tests and `lintDebug` passed. Lint success does not mean zero warnings. Three native tests passed on the dedicated API 30 emulator:

1. Real HTTP/WSS buy/cancel/refund, second authenticated client, 6-versus-2 owned tickets, countdown, 1–2 cards visible, paging, manual marking, seven-prize picker and invalid-claim feedback. The peer used `HttpRoomApi`; this was not two physical phones.
2. Fault proxy discards a committed purchase reply. After activity recreation, the encrypted pending request is unchanged; retry recovers the same table, six tickets and the 900-coin balance with no extra debit. Activity recreation is not process death.
3. Manual table regression: readable card geometry, stable pages/marks and selected-ticket/prize behavior.

Native logs: `coin-alpha16-native-04.txt` (2 tests) and `coin-alpha16-manual-regression.txt` (1 test). The earlier `-03` log records the first passing UI flow before contrast/layout refinement. Kept screenshots are from `-04`: fictional generated profile/Bina only, no credentials. They were visually inspected; the revised gold foreground and compact countdown fix the earlier contrast and redundant-card issues. Script-owned fixture processes and reverse mappings were removed afterward.

`candidate.json` records the actual tested debug APK hash and size. APK output is under `app/build/outputs/apk/debug/app-debug.apk`; it is not committed in this evidence folder. It targets **http://127.0.0.1:8080**, is debug signed, and is unsuitable for distribution to Wi-Fi players.

## Remaining release work

- Durable guest-session refresh/recovery; existing seven-day guest expiry must not strand earned coins.
- Full native successful claim/payout/results/refill/play-again flow, process-death recovery, active profile deletion/settlement and backup restore with this economy.
- Hindi/large text/compact-device checks, updated frame-time and concurrent-room latency measurements, real audio/touch on a physical phone.
- Review and apply the installed host upgrade with primary and independent journal backups, migration-owner credentials, restricted grants, readiness and real write/read checks. The existing host remains on schema 003/protocol 3; the source candidate needs schemas 004–005/protocol 4.
- Build the matching TLS LAN APK, perform two-client LAN/restart checks and complete the documented administrator firewall step. Physical-phone reachability and actual reboot remain unverified.
- Production signing, public TLS/mobile-network and Store release gates remain separate. No claim of market superiority, production readiness, permanent uptime or new latency performance is made by this checkpoint.

The computer's previously installed supervisor was observed ready with backend and TLS healthy during this turn. No live host data, credentials, startup settings or firewall rules were changed by the coin integration tests.
