# Tested server fixes on the private Wi-Fi host

Installed source: `b6d5eb6f7cc9c6c70006e4d9c422c2abe75187e0`, branch `shrey/full-tambola-game`. The upgrade completed at **23:41:38 UTC on 26 September / 05:11:38 IST on 27 September 2026**. The existing endpoint remains `https://192.168.1.4:8443`. The upgrade initially kept the alpha20 APK; the client correction below now provides alpha21.

The server now includes the shorter matching transaction, indexed generated open-lobby fields, fresh independent-journal authorization and omitted redundant snapshots on unchanged streams. Authorization, presence renewal, initial/reconnect snapshots, independent purchase receipts and eight-seat allocation remain enforced. These fixes were previously accepted by targeted service/HTTP/restricted-role/restore tests and complete isolated rounds; the review links below preserve their exact scope. No new throughput guarantee is claimed.

## Deployment and live checks

- The guarded preflight required a clean committed build, the expected private host and no unfinished rooms. It rechecked idle state after stopping the host. Both primary and journal archives were retained with old configuration/libraries before owner migrations and grants.
- A fresh restricted runtime process reached readiness. Direct read-only version checks confirm **primary schema 007**, **journal schema 003**, and HTTPS reports **room protocol 4**. Both retained backup archives match their recorded bytes/hashes and pass `pg_restore --list`. This is archive validation, not a restore rehearsal.
- The exact complete 51-JAR runtime is `f2b39e5bb622b0bd9c368da654a9412fc3e34d617e171497a1f521b7ec80ee10`; server JAR `a8488b60c8c1b638dbdeb250ec26bd8d59a332f7b415782fe4336a16c83d5d1b`. It matches the full-round and mixed-traffic tested binary.
- Verified HTTPS transactions passed: untrusted TLS and unauthenticated purchases rejected; two purchases shared a lobby; pools/prizes/computer seats were explicit; exact purchase/leave retries retained receipts and refunded each wallet once. Device enrollment and exact renewal retained the same wallet/purchase receipt, old access was rejected, both QA profiles were deleted, and a deleted device credential could not renew.

[The deployment record](deployment-state.json), [upgrade transcript](upgrade-transcript.txt) and [subsequent HTTPS checks](https-transactions.json) retain the sequence. The deployment record's TLS verification field was written before the external smoke and deliberately remains `pending external verification`; the later timestamped HTTPS evidence completes that step. Backup contents, private configuration and credentials stay outside Git.

## Native compatibility

The existing optimized alpha20 app and separate external driver matched their previously accepted APK hashes before the run. On the dedicated API30 emulator the app reached **85 calls, all eight prizes and a 2,400-coin settlement**, retained six-ticket preference and restored 20 marks after process death. The subsequent three-ticket purchase succeeded, but cancellation showed an unexpected unavailable-room dialog over a refunded 3,300-coin wallet. The driver therefore failed instead of accepting the full journey. All four QA profiles were deleted. [The failed journey](native-first-attempt/coin-release-journey.json), [transcript](native-first-attempt/transcript.txt), screenshot and UI tree are retained. No server clock or wallet was altered to complete play.

An isolated fault regression reproduced the client defect on the original alpha20 debug APK: after a committed leave response was dropped, the still-running room stream treated expected membership removal as a fault and discarded the saved command. The client follow-up stops streams while a leave is awaiting its exact receipt and guards stale room errors. The same native regression now passes, including three normal cancellations and a genuine external room removal that must still show the error.

The corrected **alpha21** APK then passed its separate complete round against this unchanged installed server: 87 calls, all eight prizes, 2,400 coins paid, 20 marks restored after process death, retained ticket preference, and the new purchase/refund without a dialog. All four QA profiles were deleted. [Alpha21 acceptance](../leave-receipt-2026-09-27/README.md) retains the packaged identity, final screenshots, metrics and cleanup. The earlier failed alpha20 journey remains a failure. [Post-test health](host-health-after.json) verifies HTTPS readiness and the exact installed runtime after the passing round.

## Capacity and release boundaries

The [unchanged-stream review](../quiet-streams-2026-09-27/README.md) records 42 passing regression tests and the complete 320-player cold round. The [mixed-traffic review](../mixed-traffic-2026-09-27/README.md) records the same runtime completing 320 players across forty tables with 347 claims, 25 shared prizes and exact balances. Three overlapping claims took at most 565 ms and 64 overlapping deliveries had p95 632 ms. Purchase p95 still failed the one-second target: 3,345 ms for the cold all-at-once burst and 1,085 ms for split arrivals. Splitting arrivals is a different workload, not an optimization.

This is a guarded **Wi-Fi testing deployment**, not production-capacity or Store acceptance. Current manual-coin endurance, actual restoration of installed backups, physical phone/firewall, router reservation, real Windows reboot behavior, public hosting and protected release signing remain open. The current-user hidden supervisor starts after login; the PC must remain signed in, awake and connected. See [host operation](../../../docs/WIFI_HOST.md) and [the APK guide](../../ALPHA_INSTALL.md).

`artifact-hashes.json` records retained review bytes; system traces remain local with identities recorded in validation.
