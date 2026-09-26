# Service deletion-recovery validation

Date: 26 September 2026. Branch: `shrey/full-tambola-game`. This is local application recovery evidence, not a production deployment. The alpha10 APK is unchanged.

This report preserves the recovery milestone's exact candidate. The later [capacity update](CAPACITY_VALIDATION.md) reruns the service suite and real-process recovery drills against its separately identified JAR.

## Tested candidate

- Service JAR SHA-256: `882192663c97ffaf3f7876051bdc5d0362328fa3a8e3674856932d1b24005f51`.
- JDK 17, PostgreSQL/server/client tools 16.9, isolated loopback cluster on port 55432. Credentials stayed in ignored fixture storage and process environment.
- Primary migrations 001–003; independent journal migration 001. Earlier migrations remain unchanged. Protocol 2, round format 3 and Android Room schema 1 remain unchanged.
- The candidate distribution, safe test summaries, exact source revision and checksums are preserved separately in `full-game/releases/service-recovery-2026-09-26/`. No prior APK package is overwritten.

## Executed checks

The final Kotlin source passed `gradlew.bat -PserverOnly=true :server:test :server:installDist --no-daemon --console=plain` in **1m 32s** with normal dependency verification. All **41 tests** passed with zero failures, errors or skips:

| Suite | Cases | Duration |
| --- | ---: | ---: |
| AvatarTest | 3 | 4.624 s |
| BackupRestoreTest | 1 | 4.495 s |
| DeletionJournalTest | 10 | 19.392 s |
| HttpTest | 4 | 6.444 s |
| NativeClientTest | 1 | 3.084 s |
| ProfileDeletionTest | 7 | 9.189 s |
| RoomServiceTest | 15 | 21.855 s |

An earlier integrated command reused unchanged passing domain/client outputs; those tasks were **UP-TO-DATE**, not fresh executions. No Android build or native suite is claimed for this server-only change. Alpha10's existing APK evidence remains in its own report.

New journal cases cover a durable intent surviving primary rollback, denied access while redaction is pending, unsuccessful replay leaving its cursor unchanged, journal-write failure preserving primary state, idempotent retries, expired confirmation with ongoing suppression, 24 concurrent append writers, concurrent replay/deletion across shared rooms, identity/cursor/gap failures, unavailable readiness during journal outage and rejection of shared primary/journal storage. Liveness remains available when recovery readiness fails.

The backup JUnit case uses actual `pg_dump`/`pg_restore`, excluding its separate journal schema. It restores an active room with an earlier audit and peer receipt after deleting its host and a second profile created after the snapshot. A positive control reads the restored old identity before recovery. Replay then rejects its access, redacts stored name/avatar copies, recovers both confirmations, preserves peer tickets/calls/scores/draw commitment and allows continued play without duplicate revisions.

`node tools/server-smoke.mjs` also passed against actual Java processes: HTTP, two sessions, private tickets, process kill/restart, durable events, idempotent retry and round ending. That ordinary fixture explicitly uses local-development mode; it is separate from journal-enabled recovery evidence.

## Actual process and database restore drill

`node tools/server-deletion-recovery.mjs` passed with run ID `177cce502f1f4ba4` and `cleanupComplete: true`. It created three fresh owned databases on the isolated PostgreSQL instance and used production-style journal enforcement:

1. Process **10212** started with separate primary/journal databases. Two fictional players began a round and called one number.
2. A **18,669-byte** primary archive was taken before host deletion. Deletion was confirmed afterward, and old access was rejected.
3. Only that owned server was killed. Only its owned primary database was dropped/recreated/restored. SQL positively confirmed that the old identity existed in the restored snapshot.
4. Startup with the third, incorrect journal failed before any HTTP listener became available.
5. Process **14920** started with the current journal, replayed before listening and rejected the old token. The peer retained its cards/calls/scores/commitment, inherited host controls and called the next number. The deleted name/avatar were redacted, the original deletion receipt matched, the primary cursor reached one and the guest row was absent.
6. All three owned databases, Java children and the temporary archive were removed. Only safe evidence metadata was retained; raw tokens, dumps and server output were excluded.

This tests separate logical databases **on one local PostgreSQL instance**. It does not establish independent infrastructure or provider backup/PITR durability.

## Failures and corrections retained

- An initial test compile used the wrong member property (`name` instead of `displayName`); it did not run application tests.
- The initial schema-filtered restore harness omitted recreation of its owned namespace. `pg_restore` failed with a missing-schema error before application replay. The harness now recreates that namespace explicitly. The failed reports remain separate from acceptance.
- The first passing complete suite contained 40 cases; an additional same-storage guard test brought the final verified source to 41. The final real-process drill used the JAR hash above.

Source logs and failed runs remain in the ignored `.test-workspace`. The release evidence contains a sanitized failure summary and passing case summaries. CI now installs matching backup tools and runs both process drills; no hosted CI execution has occurred.

## Remaining release gates

Provider/project/budget, TLS/secrets, independently retained journal backups and disaster recovery, primary PITR/cutover, least-privilege migration/runtime roles, monitoring/alerts, retention retirement, dependency/security review, ten-room load, large-history deletion latency and rollback acceptance remain. A pre-journal backup/binary cannot safely participate in this recovery policy. The Android deletion/privacy disclosure must explain retained pseudonymous recovery data before public release. See the [restore runbook](BACKUP_RECOVERY.md).

Physical phones/networks, editorial/listening/TalkBack/performance acceptance, production signing and exact signed-candidate installation/update remain broader APK gates. The production goal is active.
