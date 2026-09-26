# Automatic capacity and sustained rematches

`AutomaticSoakLoad` uses a separate real Netty process, PostgreSQL, the native HTTP/WebSocket transport and the app's `OnlineSaved` snapshot/mark/history acceptance code. It is opt-in and does not run in ordinary JUnit tests.

From `full-game/`, provide JDK 17 (including `jcmd`) and the same explicit isolated database credentials described in [capacity setup](CAPACITY.md), then run:

```powershell
.\gradlew.bat -PserverOnly=true :server:automaticSoakLoad --no-daemon --console=plain
```

The default is ten rooms with 32 players and six tickets each, nine complete 90-number automatic games, and five-second server scheduling. The same 320 native streams remain connected through all rematches. Expect roughly seventy minutes. Avoid concurrent builds, scanners or other heavy work when measuring latency. The fixture has an 85-minute cleanup-aware deadline inside a 90-minute Gradle watchdog.

The URL must be exactly `jdbc:postgresql://127.0.0.1:<port>/tambola_test`; the isolated role needs database-creation permission. The fixture creates two fresh, validated `tambola_load_*` databases and one owned loopback service. Fictional profiles are pre-seeded to separate gameplay from registration quotas. Actual create/join/ready/start/rematch/read traffic uses public APIs. It never targets a hosted service or existing app profile.

The server uses a 512 MiB heap and four active JVM processors; the generator uses a separate 768 MiB heap. JVM processor sizing is not an OS CPU quota. Both processes and PostgreSQL share the host. Protected metrics use a fresh secret held only in memory. Server output, room payloads, profile tokens and credentials are excluded from evidence.

Each game checks:

- Private, valid, stable tickets and distinct layouts within each room; a new round identity and fresh dealt layouts after rematch. Ticket IDs are player/ordinal keys scoped by round and may repeat across rounds.
- Ordered calls, hidden future order during play and verified commitment/reveal at completion.
- All calls delivered to every player, common awards/scores/results, retained marks within a round and cleared marks in the next lobby.
- Exactly one client history entry and one durable server archive per game.
- Exactly 90 stored draw events, with no gap shorter than five seconds. Events are read after each game before rolling event retention can discard older games.
- Persistent stream counts, zero handled stream failures and worker readiness, sampled every thirty seconds during play.

Delivery is measured from the stored draw event's wall-clock timestamp, which precedes commit, to the native client's receipt of the containing snapshot on the same host. This is an observed upper bound, subject to millisecond clock precision and the fixture's 100 ms wall-clock-versus-monotonic drift guard; it is not a server commit timestamp or an independently synchronized network benchmark. Every call/player contributes a sample, including calls received together in a later snapshot. The task rejects missing samples or overall p95 at or above one second; inspect the recorded distributions for each game as well.

After each game, every client enters the same empty lobby. The fixture attaches `jcmd GC.run` only to its owned server and reads the immediate post-full-collection heap from its GC log. This avoids treating uncollected garbage as retained memory. The diagnostic guard allows at most a 16 MiB span after the first warmup checkpoint. These forced collections change ordinary collector behavior; the finite range check is a regression guard, not proof that the server cannot leak. Current-heap metrics and thread counts remain separate observations. Android memory, native/off-heap memory and physical-device performance require separate evidence.

Reports under `.test-workspace/soak-<run>/` include source/compiled-fixture hashes, the complete 51-JAR runtime identity, dimensions, every game distribution, GC checkpoints, metrics, elapsed time and cleanup. Read `completed`, `cleanupComplete`, `expectedDeliverySamples`, `deliveryTargetUnder1000MsP95`, `postWarmupRetainedHeapRangeWithin16MiB` and `fullWorkloadForAtLeast60Minutes` together. The last flag requires the full ten-room/32-player/90-call workload and at least 3,600 measured seconds; a shorter or smaller probe cannot satisfy it.

Diagnostic overrides are `TAMBOLA_SOAK_ROOMS` (1–10), `TAMBOLA_SOAK_PLAYERS` (2–32), `TAMBOLA_SOAK_ROUNDS` (1–9) and `TAMBOLA_SOAK_DRAWS` (2–90). Fewer than 90 draws deliberately ends each round early so rematch and cleanup paths can be exercised quickly. Such results are fixture probes, not completed-game acceptance. Fewer than three rounds cannot establish the retained-heap guard. Clear all overrides before the default run.

The fixture cleans up only its owned streams, process and databases. If the outer process is forcibly killed, inspect its evidence and exact generated names before manually retiring any leftovers. A configured task or a passing probe does not establish full acceptance. Hosted TLS/ingress, container capacity, admission bursts, independently durable databases, mobile-network recovery and the Android 60-minute session gate remain separate.

The Android workflow exposes a separate `automatic_soak` manual input with a 100-minute job timeout and safe report/GC artifact upload. It is off by default; adding the job does not mean remote CI has executed it.

## Fixture verification, 26 September 2026

Probe `6d1ad8f1e4a0478b` completed two rooms × two players × three deliberately shortened three-call games in **52.905 seconds** of play. All **36** deliveries, histories, archives, rematches, metrics and cleanup passed; p95 was **459 ms**, and comparable post-full-GC server heaps were **11, 12, 12 MiB**. Gradle completed in **1m 33s**. Follow-up SQL found zero databases from this or the prior failed probe, and neither owned service process remained.

An initial compile failed on a missing fixture-local hash helper. A later trial `5053acc8509e4e24` correctly stopped at an overly strict cross-round ticket-ID assertion; the product scopes those IDs by round. The corrected fixture checks fresh layouts and round identity instead. The failed trial cleaned up successfully. These are fixture investigations, not product regressions or full-load acceptance. The default hour-long workload is recorded separately when complete.
