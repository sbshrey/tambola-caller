# Automatic calling and recovery fixture

`AutomaticRecoveryLoad` runs one full 90-call, 32-player room with six tickets per player. Two independently started service JVMs share fresh primary/journal PostgreSQL databases. It uses real elapsed time and the app's `HttpRoomApi`, snapshot validation, serialized `OnlineSaved` cache, manual marks and snapshot acceptance. It does not run the Android UI or Android storage/reconnect coordinator.

From `full-game/`, supply JDK 17 and `TAMBOLA_TEST_DATABASE_URL`, `TAMBOLA_TEST_DATABASE_USER` and `TAMBOLA_TEST_DATABASE_PASSWORD`, then run:

```text
gradlew -PserverOnly=true :server:automaticRecoveryLoad --no-daemon --console=plain
```

Only `jdbc:postgresql://127.0.0.1:<port>/tambola_test` is accepted. The isolated fixture role needs database creation and local migration privileges. The task creates two randomly named databases, runs only owned loopback listeners/processes and removes them afterward. Profile rows are seeded with fictional names and real hashed credentials; all room/gameplay traffic uses public APIs. Credentials and raw game/session data are excluded from the evidence.

Each service uses a 256 MiB heap and two active JVM processors. The driver has a 512 MiB heap and four active processors. These are JVM settings, not operating-system CPU quotas. The workload takes about ten minutes; it has an 18-minute cleanup-aware deadline within the 20-minute Gradle deadline. There is no reduced-call acceptance mode.

The fixture reconnects every 250 ms to exercise concurrent handshakes and cursor replay. Android's `OnlineViewModel` instead uses increasing delays with jitter, up to 30 seconds plus jitter; this workload does not measure those UI recovery times. Expected transport closures and service-unavailable responses are counted. Decode, snapshot-validation and other unexpected client exceptions fail the run.

The fixture checks:

- All 192 dealt cards are distinct and private. Future order remains hidden until the final commitment can be verified.
- Both real workers schedule the same room at five-second intervals. Manual drawing is rejected.
- A loopback proxy discards every successful pause response after upstream commit. Retrying the original command after a process restart returns the same receipt, and the database contains exactly one matching receipt and one pause event. Calls stay paused across the restart; the complete game has exactly one resume event.
- The host disconnects long enough for controls to transfer while other clients continue automatic play. Reconnection preserves the original cards/marks and does not restore old host privileges.
- One collector sleeps for 12 seconds per update. Conflation catches it up without presenting a backlog as a single new live call.
- One service is forcibly killed during play. The surviving service keeps calling; clients reconnect with their saved revision after the killed service restarts.
- Both services stop for 12 seconds. No calls occur without a worker; restarting recovers every client. Durable draw timestamps must show no duplicate or catch-up burst.
- All clients finish the same 90-number order, results and commitment. Manual marks and exactly one saved result per player survive. The old pause receipt cannot roll back newer client state.

The report in `.test-workspace/automatic-<run>/evidence.json` records progress, exact service/client/source hashes, process IDs, connections/disconnections, cadence, assertions and cleanup. A passing result requires both `completed` and `cleanupComplete`; a partial report is not acceptance. Server output and credential-bearing request bodies are never collected. The workflow's manual `automatic_recovery` input runs this same fixture and exports the report; adding that job does not mean remote CI ran.

This is one maximum-size room on one machine. It does not establish ten-room automatic capacity, hosted ingress/TLS, physical network changes, provider backup independence, production-role deployment, Android process/disk recovery, delivery-latency percentiles or a 60-minute soak. Those remain separately scoped in the [full plan](../../docs/FULL_GAME_PLAN.md).
