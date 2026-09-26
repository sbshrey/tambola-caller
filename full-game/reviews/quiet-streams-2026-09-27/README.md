# Skip unchanged live snapshots

Source `36df836` avoids building a player snapshot, reading event history, and reading the coin wallet when a live stream has already received the room's current revision. It still authenticates against the primary database and independent recovery journal, verifies membership and room lifetime, and renews presence when due. The first snapshot is always built, including when a reconnect supplies the current cursor. Changed revisions, full resyncs, reconnect events and the 500 ms polling interval retain their behavior.

This removes two SQL reads and snapshot construction from each unchanged coin poll. It introduces no authorization cache, new database schema, dependency, client protocol or APK change. It does not remove room decoding or matchmaking's serialized transaction work.

## Regression coverage

[Forty-two tests in seven suites](regression-tests.json) passed with no failures, errors or skips. Five new service tests cover:

- Quiet coin polls completing while exclusive locks block reads of wallet ledger and event history. The forced initial snapshot still contains the correct owned-ticket quantity and balance after those locks are released.
- Initial snapshots at the current cursor, changed-event deltas, missing/future-cursor resync and invalid-cursor rejection.
- Presence renewal without a redundant snapshot, followed by a real reconnect revision and event.
- Membership removal, room expiry and credential expiry even when the supplied revision matches.
- Fresh independent logout/deletion suppression, an inconsistent recovery cursor and journal outage on the unchanged path.

The HTTP tests also require an initial WebSocket snapshot for a current cursor, observe quiet intervals without duplicate frames, and then receive the next draw with only the player's tickets. A new live WebSocket test records logout in the independent journal and requires the quiet stream to close as unauthorized before primary replay. Existing polling, admission, journal-authentication, coin-match and operations tests passed too. The first compile attempt used the wrong property name in two new test assertions; [the compile error](test-compile-error.txt) and [corrected passing run](regression-tests-transcript.txt) are retained.

## Purchase comparison

Both unprofiled probes used the same existing fixture and clients: 320 seeded players, forty full tables, immediate WebSocket subscriptions during purchases, 1–6 tickets, normal sales deadlines, and two purchase/cancellation waves on the same server. Every player received a subscription snapshot. Exact receipts, refunds, a single debit per purchase and aggregate conservation passed. These waves do not test completed-round rematching or endurance.

| Condition | Cold purchase p95 | Repeat purchase p95 |
| --- | ---: | ---: |
| Baseline `4bca9bd`, run `d00089dba3844b19` | 3,454.106 ms | 1,227.005 ms |
| Candidate `36df836`, run `90db27039fb348c7` | 3,184.284 ms | 1,271.486 ms |

The cold sample was lower and the repeat sample slightly higher. This pair does **not** establish a consistent purchase-latency improvement or close the sub-second target. The avoided reads are verified separately by the lock regression; fewer queries must not be equated with a measured end-to-end speedup.

The baseline runtime is `9884bbf0a6017448372605e3c0314c478291a7c16a692882a64c0917329a1b8b`. The candidate server JAR is `a8488b60c8c1b638dbdeb250ec26bd8d59a332f7b415782fe4336a16c83d5d1b`; its full 51-JAR runtime is `f2b39e5bb622b0bd9c368da654a9412fc3e34d617e171497a1f521b7ec80ee10`. Each raw evidence file retains source/client hashes, flags and the full runtime manifest. See [the fixture guide](../../server/COIN_LOAD.md) for reproduction and measurement boundaries.

## Full-round acceptance

Unprofiled full run `d266886a271a402e` used the same final candidate runtime at the real five-second pace, with cold wallets and all diagnostic flags off. It completed forty tables, 320 synthetic players and 1,116 tickets, with **349 accepted claims**, **33 shared prizes**, **441 exact receipt replays**, forty delayed acknowledgement retries, four peak concurrent claims and zero closed claim windows. All player results and wallets matched independently calculated prize shares. The run settled 111,600 coins, preserved aggregate balance 480,000, and verified all **25,056/25,056** expected call deliveries. Tables completed after 70–84 calls.

| Full-round client measure | p50 | p95 | p99 |
| --- | ---: | ---: | ---: |
| Purchase RTT | 2,105.691 ms | **3,344.685 ms** | 3,421.389 ms |
| Claim RTT | 11.446 ms | 38.493 ms | 63.198 ms |
| Draw event → snapshot | 298 ms | 522 ms | 551 ms |

Gameplay correctness and cleanup passed. The task correctly exited **1** because purchase p95 remains above 1,000 ms. The results do not establish a repeatable purchase speedup. [Raw results](full-run/evidence.json), [the failed performance transcript](full-run/transcript.txt) and GC data are retained. The complete runtime identity above was verified unchanged after play; no later source change is being substituted for this tested binary.

Draw delivery measures the persisted event timestamp, captured before commit, to each native client's first snapshot containing the call. It is not scheduled-deadline, Android, TLS or physical Wi-Fi latency. Profiles were seeded; synthetic clients manually submit revealed owned numbers. This is not registration, phone input or restricted-role acceptance. All purchases occur before the claim phase, so this fixture does not yet prove claim latency while a new cohort is buying tickets. The next load scenario will measure that overlap within the same 320-player ceiling before choosing further admission changes.

[Independent cleanup](cleanup.json) found all three recorded server PIDs and six exact isolated databases absent, with no random test schemas remaining in the control database. [Fresh host health](host-health.json) returned verified HTTPS 200/ready, with the complete installed runtime unchanged. Diagnostic launchers and tests are absent from the candidate server JAR. This candidate is committed but **not deployed**; the alpha20 APK, installed schema, firewall and signing remain unchanged. Current manual-coin endurance, installed-backup restoration, physical Wi-Fi/firewall, actual reboot behavior and public release gates remain open.

`artifact-hashes.json` covers the retained review bytes. The compile error and failed latency outcome remain alongside the passing correctness checks.
