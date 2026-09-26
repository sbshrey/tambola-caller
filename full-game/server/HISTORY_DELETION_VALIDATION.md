# Retained-history deletion validation

26 September 2026. Branch `shrey/full-tambola-game`. This is a service update; the alpha12 APK, protocol 2, primary/journal migrations and runtime grants are unchanged.

## Problem and behavior

Deletion previously materialized every affected room, archived round and peer command response as a list of decoded objects. A profile with many retained responses could exhaust the process heap. The bounded fixture reproduced this in a 128 MiB JVM: 20,000 responses containing 49,608,894 bytes reached 133,042,952 bytes of sampled heap and failed with `OutOfMemoryError`, also disrupting the unrelated room. Run `4eb70952` failed; cleanup succeeded.

Deletion now reads these records through forward-only cursors with a fetch size of 32, inside the existing single primary transaction. Each row is decoded, redacted and updated before moving on. Room locks remain ordered by ID; archived rounds and responses have stable primary-key order. No key used by an open cursor is changed. PostgreSQL's driver requires a non-autocommit connection for cursor fetching; the helper enforces that requirement. See the [pgJDBC cursor documentation](https://jdbc.postgresql.org/documentation/query/#getting-results-based-on-a-cursor).

The first cursor-only candidate passed the 20,000/50,000-response checks with no background worker. Including the real `RoomWorker` exposed a second failure: recovery waited on the guest row already owned by foreground deletion. The five-second lock timeout was treated as a recovery failure, temporarily rejecting unrelated requests. Run `a65d26e5` recorded one worker failure and five unrelated-room failures while deletion itself completed. It is failed evidence, not acceptance.

Background recovery now defers a locked replay cursor or guest row using `SKIP LOCKED`. It leaves the replay cursor unchanged and continues other worker steps. A missing guest still receives redaction/replay because retained records may come from a restore. A missing recovery cursor remains an error. Busy work cannot clear an earlier genuine recovery failure: only catching up successfully clears that state. Startup continues to use strict blocking replay before opening HTTP. The independent journal continues to suppress the deleted identity throughout an unfinished operation.

## Accepted bounded workload

The fixture inserts synthetic but serialized domain records, then calls the real `RoomService` and PostgreSQL implementation. It uses 70 retained rooms, 139 archived records, two players with six tickets each, and 50,000 peer command responses. The historical peer previously hosted those responses; their original command IDs/hashes remain valid for retry. One unrelated lobby repeatedly reads and changes readiness, while the normal background worker runs. The fixture, service, worker and sampler all share the constrained JVM.

| Measurement | Final run `821b830e` |
| --- | --- |
| JVM heap ceiling | 134,217,728 bytes / 128 MiB; four active processors |
| Saved response payload | 124,138,894 bytes (before database compression) |
| Deletion attempt | 10,991.4123 ms |
| Sampled peak heap | 86,180,520 bytes / about 82.2 MiB; 347 samples |
| Concurrent unrelated read/command pairs | 10 succeeded; zero failures; maximum pair 388 ms |
| Background worker failures | Zero |
| Cleanup | Both generated schemas removed and pools/threads closed |

The result checks redaction across every retained record, removal of guest/participant access, peer identity preservation, unchanged tickets/calls, exact original command retry, one durable suppression record, stable deletion confirmation, host transfer and a subsequent resumed draw. Five ordinary regression cases also cover multiple cursor pages, failure on the final response after earlier pages were updated, complete primary rollback, subsequent journal replay, busy guest/cursor deferral, preservation of genuine failure state and rejection of a missing cursor.

This is a bounded direct-service/JDBC measurement with a same-database separate journal schema, not an HTTP latency benchmark, a 32-player maximum-payload workload, a long soak, a multi-process contention test or proof of independent provider backups. Sampled heap is not an exact allocation high-water mark. The heap ceiling is enforced by the JVM. Records are generated afresh, so payload byte counts vary slightly between runs. Affected rooms remain locked until the atomic deletion commits; this change does not promise uninterrupted commands in those rooms.

## Candidate identity and broader checks

- Service JAR SHA-256: `a1307739a2c83c47acb1176c2ea61a2dd0e6ac28c499e9803e4f0ffd8bed5b3a`.
- Service ZIP SHA-256: `507eac3141b390ca527cc738d8ad494b517b4098f78a41438d161e07e5c0989e`.
- Measured `RoomService.class` SHA-256: `3d29df5053bb894f9f91c0651b819198e74d7ec8d48830eb7165b1ff61f23ac8`; extracted JAR class and distribution-bundled JAR match the measured candidate.
- All **67 service/PostgreSQL tests passed**, with zero failures/errors/skips, in **4m 6s**. This includes the five new history/concurrency regression cases. Unchanged Android/domain/client tests were not rerun for this service-only slice.
- Restricted-role process run **`b9739bdadaa240bb`**, PIDs **3124 → 12176**, passed startup grant checks, two-player gameplay/restart/deletion, destructive-SQL denial, protected metrics and recovery from a deliberately revoked SELECT grant. Cleanup completed.
- Ordinary real-HTTP restart smoke passed: two sessions, private cards, actual process kill/restart, durable events, exact command retry and round completion.
- Primary-only restore run **`ad9ea4bf668c4969`**, PIDs **15248 → 12808**, restored the deleted identity from an **18,706-byte** archive, rejected the wrong journal before listening, then replayed the current journal before serving. Original confirmation and peer continuation passed; cleanup completed. Both accepted process reports identify the final JAR above. This remains a local logical-restore drill, not provider PITR or independent-infrastructure acceptance.
- No APK rebuild, new dependency, migration, permission, paid generation, remote CI run or public deployment belongs to this slice. Earlier alpha/service packages retain their original identities.

Investigation evidence also retains the Gradle script import error (`java.time` shadowed by the Java extension) and a fixture assertion that tried to authenticate the deliberately locked guest before releasing its row lock. The latter correctly hit the existing lock timeout; the corrected test checks suppression after releasing that lock. Neither failed run is counted as acceptance.

The first permission run, `a213253ef47849a6`, completed its functional checks but failed automated cleanup while the full service suite was also running. The helper did not preserve a specific cleanup cause. Inspection found both owned databases already absent, no owned database sessions, and four run-specific roles remaining. Those four roles were removed explicitly; a separate cleanup note records zero matching databases/roles afterward. The original failed-cleanup report remains unchanged. Acceptance uses the sequential rerun above, performed after the full suite finished, followed by the ordinary restart and restore drills.

## Reproduce

Supply `TAMBOLA_TEST_DATABASE_URL`, `TAMBOLA_TEST_DATABASE_USER` and `TAMBOLA_TEST_DATABASE_PASSWORD` for the isolated loopback `tambola_test` PostgreSQL instance. Do not use a production database. The load entry point rejects remote hosts and other database names. Fixtures create random schemas and clean up their own resources.

```text
gradlew -PserverOnly=true :server:test --tests io.github.sbshrey.tambola.server.HistoryDeletionTest --tests io.github.sbshrey.tambola.server.BusyDeletionReplayTest
gradlew -PserverOnly=true :server:historyDeletionLoad
```

The load task fixes its process heap at 128 MiB and active processor count at four. `TAMBOLA_HISTORY_RECEIPTS` accepts 130–50,000; default 20,000. Set it to `50000` to reproduce the larger worker-contention workload. Evidence is written under `.test-workspace/history-delete-*/evidence.json`, including failures and cleanup status. The service CI job now runs the default bounded-heap fixture and retains its evidence; it has not been executed remotely for this change.

Hosting/TLS/secrets/monitoring, real HTTP and maximum-payload deletion measurements, broader load/fault/soak, multi-process deployment, independent backup/PITR and journal retirement, physical-phone acceptance and production signing remain in the [full plan](../../docs/FULL_GAME_PLAN.md).
