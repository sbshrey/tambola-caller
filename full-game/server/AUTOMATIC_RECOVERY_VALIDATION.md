# Automatic calling and recovery validation

26 September 2026. Branch `shrey/full-tambola-game`. This adds an opt-in recovery fixture and manual CI job. Service, client and APK runtime code, dependencies, protocol, migrations and grants are unchanged.

## Accepted real-clock game

Run **`7bf52b1a67cb4335`** completed the entire automatic game and cleaned all owned processes, loopback listeners and two fixture databases. It used **32 native clients, 192 distinct private tickets, two service JVMs and all 90 calls**, with the ordinary background workers and a five-second call interval. The servers share one isolated PostgreSQL instance but use separate primary/journal databases. Each server has a 256 MiB heap and two active JVM processors; the driver has a 512 MiB heap and four active processors. Processor settings are not OS CPU quotas.

| Observation | Accepted result |
| --- | --- |
| Execution including cleanup | 506.448 seconds |
| Native clients finishing all calls | 32 of 32, each with 90 numbers |
| Durable draw events | 90; final order is exactly the numbers 1–90 once each |
| Durable pause / resume events | Exactly 1 / 1 |
| Successful pause responses discarded | 1 |
| Minimum / maximum stored draw-timestamp gap | 5048 / 17884 ms |
| Five calls with one surviving service | 20987.013 ms |
| Both services stopped | 12047.287 ms, with no persisted call advancement |
| Backlog snapshots with no live-call announcement | 20 |
| Cleanup | Complete |

The HTTP fixture dropped the successful pause response after commit. Calls stayed paused while one service was killed and restarted; the original request then returned the same receipt through both service instances, with exactly one stored receipt and one pause event. Resuming produced one resume event. A disconnected host lost controls after the normal presence timeout while automatic calling continued; reconnecting preserved that player's cards and manual marks, and host-only commands were rejected.

A native collector delayed each update by 12 seconds and then caught up through the client's conflated stream. Snapshot validation, prefix checks, private-card validation and manual marks stayed intact; multi-call backlog snapshots produced no single live-call announcement. Killing one active service left the other making calls. Stopping both services for more than two intervals froze stored progress; restarting recovered every client without duplicate durable draws or a stored-timestamp catch-up burst.

Every client's final state passed native boundary validation, including the revealed order's commitment. All clients agreed on calls, awards and scores, retained manual marks and stored one result for the round. Reapplying the old pause receipt could not roll the original host's newer cache backward. Decode, malformed-snapshot and unexpected client errors are fatal in this final fixture. Expected connection failures during injected outages were counted by type: `ConnectException`: 3653, `SocketException`: 64. Each client uses an explicit 250 ms fixture retry loop; this does not measure Android's backoff/jitter coordinator or UI recovery time.

## Identity and investigation

- Service JAR SHA-256: `a1307739a2c83c47acb1176c2ea61a2dd0e6ac28c499e9803e4f0ffd8bed5b3a`; byte-identical to service implementation commit `e163d67e5c131f3ddef8df58ce25701bf306f0eb`.
- Distribution ZIP SHA-256: `507eac3141b390ca527cc738d8ad494b517b4098f78a41438d161e07e5c0989e`.
- Native client JAR SHA-256: `986fd82da15d3614ea516bda543a7356914e5eeeec0ed0ae146b185c52c8d248`.
- Executed fixture source SHA-256: `b2d49ec000a7bdf5f0db79491830c4bc8c7c07a79339af82538e2c74c924ff45`.
- Owned service process IDs across starts: 19020, 15376, 3984, 16564, 18892, 16648. The report records each client's successful connections and failed/closed connection attempts.

Initial run **`c2855d3feb4e4b58`** also completed all 90 calls and cleaned its resources in an **8m 52s** build/task invocation. It measured stored draw gaps of **5,056–15,548 ms**. Its original source/evidence is retained separately. Review then narrowed reconnect exception handling so an unexpected decode/runtime error could not be treated as a transient transport failure, added explicit pause/resume counts and per-client final counts, and bounded fixture JDBC connection/socket waits. Final acceptance is the complete rerun above, not a relabelled initial report.

The service and client JARs are unchanged. The prior 67 service/PostgreSQL cases, ten-room manual capacity test, constrained-container checks and alpha12 Android tests retain their earlier candidate identities and scope; they were not newly run for this test-only slice. No APK rebuild, paid generation, remote CI execution, push, hosting change or public release occurred.

## Reproduction and limits

Follow [the fixture instructions](AUTOMATIC_RECOVERY.md) and run `gradlew -PserverOnly=true :server:automaticRecoveryLoad` from `full-game/` with the explicit isolated PostgreSQL test credentials. The manual workflow input is `automatic_recovery`. Evidence is saved under `.test-workspace/automatic-<run>/evidence.json`; completion and cleanup must both pass. The test cannot be directed at a public host. Fictional profiles are pre-seeded; room and gameplay traffic uses public APIs.

This establishes one maximum-size room with two local service processes and real native transport, not ten-room automatic capacity, an Android UI/device network test, hosted ingress/TLS, distributed-clock/provider behavior, restricted-role deployment, a 60-minute soak or delivery-latency percentiles. A slow application collector is distinct from a physically bandwidth-limited socket. Draw cadence here means stored service timestamps; it is not an end-to-end caller/audio latency measurement. Cold Android process/storage recovery remains in its separate report.

Hosted operations, provider recovery and journal retirement, wider capacity/fault/soak, device/accessibility/editorial/audio acceptance, advisory review and production signing/privacy/support/store requirements remain open in the [full plan](../../docs/FULL_GAME_PLAN.md). The production goal remains active.
