# Ten-room gameplay capacity validation

Date: 26 September 2026. Branch: `shrey/full-tambola-game`. This report covers local service/client protocol capacity; it is not hosted or physical-phone acceptance. The alpha10 APK is unchanged.

## Workload and measurement

Ten rooms each used 32 distinct fictional profiles and six tickets per player: **320 live native-client WebSocket streams and 1,920 server-dealt tickets**. Every room played all 90 numbers with concurrent manual draws at a minimum five-second interval. All 320 clients checked valid private snapshots, monotonic calls, hidden future draw order and matching final calls/awards/scores/commitment. All 192 cards within each room were distinct at deal time. Each call produced one latency sample per client, for **28,800 expected delivery samples**.

The driver uses the app's `HttpRoomApi` implementation over actual HTTP/WebSockets to a separate Netty Java process. The primary and enforced deletion journal occupy fresh independent logical databases on one isolated PostgreSQL 16.9 instance. Profiles are pre-seeded with correctly hashed fictional credentials to isolate gameplay from registration throttling; all room/game traffic uses public APIs.

Both runs use JDK 17.0.14 on the same Windows host. The service has a 512 MiB maximum heap and JVM active-processor count of four; the driver reports six available logical processors and has a separate 768 MiB limit. PostgreSQL, driver and server share that host; the original emulator remained running without test activity. No other builds or heavy tests run during measurement. Processor count controls JVM sizing, not an OS CPU quota.

Delivery latency starts before the command's HTTP revision read and ends when the native client receives the decoded snapshot. This is an upper bound on commit-to-client delivery. A separate lower bound starts after the successful HTTP command response. Both use one monotonic client clock. Call timing waits for every player before continuing, so slow delivery extends the run rather than disappearing from its sample set. These measurements exclude Android rendering and real mobile networks. See [fixture instructions](CAPACITY.md).

## Baseline

- Service JAR `882192663c97ffaf3f7876051bdc5d0362328fa3a8e3674856932d1b24005f51`, committed source `5f6385a933454260805df3f84476842c5e3682f1`.
- Run `9a3e4afdf5fa4586`, server PID 15828, fixture class fingerprint `a0fed19360b2a321c31ff44a2d41741e8d58f7f9b8e52e93b97681ae16a65d27`.
- All games and result comparisons completed; all **28,800** delivery samples were present; owned process/database cleanup completed.
- Dispatch-to-snapshot: **p50 604.5 ms, p95 1,070.7 ms, p99 1,305.0 ms, maximum 1,535.2 ms**. Every room's p95 exceeded one second.
- Acknowledgement-to-snapshot lower bound: **p95 987.6 ms**. Thus the true commit-to-client p95 lies between about 988 and 1,071 ms; the one-second target is **unproven**, rather than conclusively failed or met.
- Command round trip, including revision read: p95 **136.1 ms**. Gameplay took **446.330 s**; server CPU during that interval was **409.125 CPU-seconds**. The complete Gradle invocation took **8m 5s**.
- There were **2,194** post-collection heap samples, ranging from **2–95 MiB**, ending at **38 MiB**. These include setup and young collections; they do not establish retained-heap size or a 60-minute soak.

The baseline driver returned success for completed games even though `deliveryTargetUnder1000MsP95` was false. That is preserved as historical behavior, not a latency pass. The current driver additionally exits unsuccessfully for a missing sample, failed cleanup or conservative p95 at/above one second, and the manual CI job uses that guard.

## Candidate changes and verification

The service polls authenticated committed state every **500 ms**, leaving time for SQL and transport within the one-second delivery budget. Every refresh authenticates, checks membership and reads committed PostgreSQL state; automatic-caller scheduling retains its existing timing. This increases idle stream reads to approximately 120 per minute per connection, within the existing 300-per-profile read limit; multiple connections share that quota.

SHA-256 text encoding now uses JDK 17 `HexFormat` instead of constructing a formatter for each of 32 digest bytes. Lowercase hexadecimal values, migration checksums, credential/receipt hashes and draw commitments keep the same format. The APK's domain/protocol/client code is unchanged. The paired workload compares these changes together; it does not separately attribute improvements to each.

Candidate service JAR: `54740e1266ef91df641648a90c9e6fa15fb7711ef15dddf18383a3a7738d82a2`.

The candidate passed all **41 server/PostgreSQL cases**, zero failures/errors/skips, in **1m 36s**. It also passed the ordinary real-process restart/receipt smoke against the existing fixture database and the journal-enabled primary restore drill. The latter is run `c7ed85006fdc4172`, processes **5804 → 16400**: the wrong journal prevented listening, the restored deleted profile was suppressed/redacted, original confirmation and peer game state survived, and cleanup completed. Domain/client/Android suites were unchanged and were not newly executed in this slice.

## Full candidate result

Run **`424c0d28ce5f40c9`**, server PID **17356**, fixture class fingerprint `a77a55924aaf5e13901b80cc52447d08afa7676ed4b268222aade4168f616eaa`, completed all games, result comparisons and **28,800/28,800** delivery samples. Cleanup completed and the enforced conservative latency gate passed. The complete invocation took **8m 9s**; gameplay took **446.023 s**.

| Metric | Baseline: 1,000 ms polling | Candidate: 500 ms polling |
| --- | ---: | ---: |
| Dispatch-to-snapshot p50 | 604.5 ms | 395.7 ms |
| Dispatch-to-snapshot p95 | 1,070.7 ms | **708.3 ms** |
| Dispatch-to-snapshot p99 | 1,305.0 ms | 857.3 ms |
| Maximum dispatch-to-snapshot | 1,535.2 ms | 1,327.3 ms |
| Acknowledgement-to-snapshot p95 lower bound | 987.6 ms | 521.3 ms |
| Command round trip p95, including revision read | 136.1 ms | 277.2 ms |
| Server CPU-seconds during gameplay | 409.125 | 643.563 |
| Average server CPU-seconds per wall second | 0.92 | 1.44 |
| Post-GC heap range / final sample | 2–95 / 38 MiB | 2–113 / 50 MiB |
| Recorded GC pause count / maximum pause | 2,194 / 12.922 ms | 3,651 / 13.550 ms |

Every candidate room's p95 was below one second (**681.5–748.0 ms**). The p95 upper bound establishes the planned local commit-to-client latency target for this workload. Individual tail deliveries can still exceed one second; this is a percentile target, not a maximum-latency guarantee.

The latency reduction costs approximately **57% more server CPU time** in this pair, consistent with more frequent database reads. Command round-trip p95 also increased. The measured average CPU demand remained below the four-processor JVM sizing value, but this is not a hard four-vCPU quota test or a hosted cost estimate. Heap observations include young collections/setup; neither run proves retained memory stability over an hour. Raw safe GC logs and per-room distributions remain in the evidence.

The candidate artifact/evidence package is `full-game/releases/service-capacity-2026-09-26/`; `SOURCE.json` records its committed source revision and exact JAR fingerprint. The prior recovery and APK packages remain unchanged. Fixture changes between the paired runs add the exit-code gate, platform-neutral scope text and fresh scratch-directory creation; the two latency formulas and workload dimensions are the same.

## Earlier fixture failures

- Initial fixture compilation shadowed the `java.net` package with a local path variable. A later Windows startup attempt passed a wildcard to `Path.resolve`, which rejects `*`. Both failed before gameplay and were corrected in the fixture.
- Run `88c7246493104cb2` deadlocked the driver's Default workers in Ktor 3.3.3's blocking nonce bridge before WebSocket traffic reached the service. A thread diagnostic and zero read/command rate buckets established that failure. Only owned driver 19548/server 11940 and the two exact generated databases were manually removed. The driver now uses IO for its many simulated connections and bounds cleanup waits.
- The corrected eight-client/five-call probe `15db2396431d492f` completed its 40 samples with p95 **1,051.0 ms**, cleanup complete. It was a fixture check, not full-capacity acceptance.
- Scratch directory creation was made explicit for fresh checkouts in both the load and recovery drivers. The workflow definition includes the opt-in capacity job; no hosted CI run has occurred.

## Remaining scope

Hosted TLS/ingress and independent journal infrastructure, multi-process deployment, mobile/physical clients, slow consumers and reconnect storms, automatic calling at capacity, large retained-history deletion, longer soak, production permissions/monitoring and rollback remain separate gates. Client UI frame/launch/mark budgets, TalkBack/editorial/audio acceptance and production signing remain broader APK work. The full production goal stays active.
