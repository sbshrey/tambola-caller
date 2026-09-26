# Alpha18: personal winnings and idle timer work

App source: `e4ac50422bb4856a59086b4394a40be3f21ab6e7`. The installed Wi-Fi service remains on alpha17 source `9fe335d47b5b76688c0a148f64e074af4630fbdf`, primary schema 006, journal schema 003, protocol 4. No service migration, restart or protocol change was needed for this UI update.

## Changes

Previously, results put a tick beside every awarded prize, even when only another player won it. A tied prize also showed the advertised pool amount instead of this player's share. Results now show only prizes won by owned tickets, combine shares across those tickets, and label prizes shared with opponents. Unclaimed coins remain separate from winnings. The live table's prize overview still shows the advertised schedule and global claim status.

The domain's existing deterministic ticket-ID remainder calculation is shared by settlement and display. A 40-coin prize split across three winning tickets yields 14, 13 and 13; owning two of those tickets can therefore show 27, not 40. Server acceptance tests verify that settlement, conservation and idempotency remain intact.

The lobby no longer runs a clock on entry or finished-result screens. Queue, refill and next-call timers own their remaining time, stop at zero, and use a monotonic elapsed clock after anchoring to a snapshot. Captions observe whole seconds; the progress bar reads remaining time in its draw callback. This follows Android's guidance on [deferring state reads](https://developer.android.com/develop/ui/compose/performance/bestpractices) and [Compose rendering phases](https://developer.android.com/develop/ui/compose/performance/phases).

## Targeted checks

- 50 domain tests, 14 Android unit tests (including five new result cases), and 20 PostgreSQL coin ledger/match tests pass. Cases include opponent-only awards, uneven ties, multiple owned winning tickets, deterministic remainder ordering and restored-engine payout totals. Test databases were isolated from the installed host.
- LAN/debug fixture APK builds and LAN lint pass; lint reports zero errors and 90 warnings. The development signing certificate matches alpha17.
- Three native screen checks pass on the debug test-harness variant: own shared amount versus opponent awards, removal of stale prize cards, one-tap replay selection, English, Hindi at 130% text, and a countdown reaching zero then accepting a new server deadline. Screenshots were visually inspected. The first attempt on the LAN variant could not launch the standalone fixture activity because the Compose test manifest is a debug-only dependency; no application change was needed.
- The exact LAN candidate passes the idle guard and Android HTTPS/WSS transport check. Its public host CA is scoped to the private address; no TLS bypass or adb reverse is used.

## Idle measurement

`idle-live-baseline.json` records **64 composition passes in 3,202 ms** on the installed alpha17 APK. `idle-live-candidate.json` records **zero in 3,200 ms** on alpha18. Both attach the same `CompositionObserver` to three registered compositions in a real `ActivityScenario`, without a Compose test rule. They use the same empty disposable emulator profile and runner settings. This demonstrates removal of idle composition work in this sample; it is not an active-game frame-rate, battery or physical-device measurement.

The earlier `idle-baseline.*` and `idle-observer-baseline.*` probes returned zero while a Compose test rule controlled the coroutine clock. Sleeping in those tests did not advance the app timer. Those files are retained as ineffective probes, not used as the before/after comparison. The final test uses real elapsed time and asserts that its observer attached before measuring. See the official [composition observer API](https://developer.android.com/reference/kotlin/androidx/compose/runtime/tooling/CompositionObserver).

## Full Wi-Fi round

The exact packaged LAN APK passed `LanCoinGameTest` in **469.335 seconds** with animations enabled: **86 calls**, six disjoint owned tickets, manual marking/paging, all eight selected prizes and **2,400 coins paid**. The final native wallet was **3,300** (1,500 starter minus 600 purchase plus 2,400 winnings). Four authenticated QA identities conserved **6,000 coins** in total. Play Again bought three tickets for 300, and cancelling that new lobby returned exactly 300. Three passive HTTP QA peers funded this round; this scenario did not include computer opponents.

JUnit passed after deleting all four QA profiles and restoring preferences. The runner restored system animation settings. A subsequent read-only idle guard confirmed that the local profile was empty and no idle composition work remained. The installed APK was read back and matched `candidate.json`; no adb reverse mappings remained. All 51 installed service libraries still matched the alpha17 manifest, and fresh supervisor/private-CA HTTPS health checks passed. The host was not upgraded or interrupted.

FrameMetrics observed **5,763** non-first-draw frames, excluded six first draws, and reported no dropped or unavailable measurements. The p95 upper bucket was **51 ms**, maximum **205.31 ms**, and **3,963** frames exceeded 16.67 ms. This is not a smoothness acceptance pass. Different round lengths/draws and the debug software emulator prevent treating this as a controlled comparison with alpha17's active-game frame result.

The report's current-room call/claim counts are zero after leaving the new lobby. `finishedCalls`, payout fields and the native assertions record the completed round. `first-win.png`, `results.png` and the English/Hindi shared-prize fixtures were visually inspected.

## Release boundaries

The APK is a private-network development build, not a Store release. Physical-phone/router reachability, optimized active-game frames, native refill acceptance, lost-device recovery, actual Windows reboot and installed-backup restoration remain open. The Windows private-network firewall setup still requires an administrator. See [host operation](../../../docs/WIFI_HOST.md) and [the active release plan](../../../docs/ONLINE_COIN_GAME_PLAN.md).

`candidate.json` identifies source, app/debug-fixture/instrumentation bytes, certificate and public CA. `SHA256SUMS` covers the retained evidence. Evidence contains no access/device tokens, private keys, protected configuration or raw databases.
