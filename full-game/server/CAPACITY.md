# Reproducible gameplay capacity fixture

`ServiceLoad` is an opt-in workload against a real, separate Netty Java process, real PostgreSQL and the Android app's `HttpRoomApi` HTTP/WebSocket transport. It is not part of ordinary JUnit execution. Results belong to the exact measured candidate and host; a completed run alone does not satisfy a latency target or prove hosted capacity.

From `full-game/`, supply JDK 17 and explicit `TAMBOLA_TEST_DATABASE_URL`, `TAMBOLA_TEST_DATABASE_USER` and `TAMBOLA_TEST_DATABASE_PASSWORD`, then run:

```powershell
.\gradlew.bat -PserverOnly=true :server:loadTest --no-daemon --console=plain
```

The URL must be exactly `jdbc:postgresql://127.0.0.1:<port>/tambola_test`. The isolated fixture role needs `CREATEDB`. This task creates fresh primary/journal databases with generated, validated names, starts only its owned loopback server and removes those databases/processes afterward. It cannot be pointed at a hosted service. Credentials stay in environment/memory. Safe result metadata and the owned server's GC log are written beneath `.test-workspace/load-<run>/`; raw sessions, server output and database payloads are excluded.

Default workload:

- Ten rooms, 32 distinct profiles per room, six unique tickets per player: **320 live streams and 1,920 tickets**.
- Fictional guest rows are pre-seeded with real hashed credentials. All create/join/ready/start/read/draw/result traffic uses public APIs. This isolates gameplay capacity from the deliberately separate 60-per-minute registration quota; it does not measure onboarding bursts.
- Every player opens the real client event stream and verifies its own tickets, monotonic calls, hidden future order and valid snapshots. All 90 calls must reach every player, with matching results across each room. The fixture validates all cards are unique within the dealt room.
- Calls are dispatched concurrently across rooms, no sooner than every five seconds. Each next call waits for all viewers to catch up, so overload stretches the run rather than silently omitting slow clients. Inspect total duration and worst-case latency, not just the percentile.
- The service uses `-Xmx512m` and `-XX:ActiveProcessorCount=4`, with unchanged database pools. Processor count affects JVM sizing; it is not an operating-system CPU quota. The load generator uses a separate JVM with one shared transport per room. Both processes and PostgreSQL share the local host.

The delivery metric uses one monotonic clock in the client workload: command dispatch before its HTTP revision read through receipt of a snapshot containing that call. It includes command execution/commit, polling and transport, so it is an upper bound on commit-to-client delivery. Every call is sampled for every player; a later snapshot that contains multiple calls gives each missed intermediate call its actual observed delay. The report also includes command round-trip times, per-room distributions, sample count, progress and server CPU duration.

The plan's provisional target is p95 below 1,000 ms for committed-event visibility. Meeting this stricter dispatch-to-snapshot bound meets that local latency target; failing the bound needs investigation rather than automatically proving commit-to-delivery violated it. The separate acknowledgement-to-snapshot metric starts after the successful command response, so it is a lower bound on commit-to-delivery (clamped at zero if the stream arrived first). A p95 above 1,000 ms for that lower bound establishes a missed local delivery target. Read `completed`, `cleanupComplete`, exact dimensions, `expectedDeliverySamples` and `deliveryTargetUnder1000MsP95` together. The current task exits unsuccessfully if correctness, cleanup, sample count or the conservative p95 target fails. The initial baseline predates that exit-code guard and explicitly reports its unmet latency flag. A non-default short probe does not establish the default workload's acceptance.

Optional bounded environment overrides support fixture diagnosis: `TAMBOLA_LOAD_ROOMS` (1–10), `TAMBOLA_LOAD_PLAYERS` (2–32), `TAMBOLA_LOAD_TICKETS` (1–6), `TAMBOLA_LOAD_DRAWS` (1–90) and `TAMBOLA_LOAD_INTERVAL_MS` (2,000–10,000). Always record overrides. GC logs provide post-collection heap samples without forcing collection; an eight-minute game does not establish the separate 60-minute memory-soak requirement.

The many simulated client collectors run on the IO dispatcher. An initial fixture used Default, where simultaneous Ktor 3.3.3 WebSocket handshakes blocked all available workers in the nonce bridge before its producer could run. A thread diagnostic established that generator deadlock before any WebSocket/read/command reached the service. The failed probe and verified manual cleanup remain separate evidence. Shutdown now closes transports and bounds its collector wait before removing owned resources.

The Android workflow has an opt-in `room_capacity` manual input. It starts an isolated PostgreSQL service, runs this same default workload and exports only safe evidence JSON/GC logs. Defining that job is not evidence of a hosted CI execution.

Provider TLS/ingress, different mobile networks, multi-process deployment, automatic caller scheduling at capacity, admission/abuse load, slow consumers, reconnect storms, retained-history deletion, sustained soak and rollback remain separately scoped checks. This fixture creates no cloud resources and does not alter APK assets or game rules.
