# Room-service candidate validation

This report records the initial service milestone; its remaining-work statements are historical. See [alpha03 validation](../ALPHA03_VALIDATION.md) for the later 19-test suite/native client, and [recovery validation](RECOVERY_VALIDATION.md) for the current 41-case service suite and real-process backup/deletion recovery drill.

Date: 25 September 2026. This report covers local source validation, not a hosted service or a production APK.

## Observed checks

- JDK 17, Kotlin 2.2.21, Ktor 3.3.3, JDBC 42.7.13 and an isolated PostgreSQL **16.9** cluster bound to `127.0.0.1:55432`. Database `tambola_test`; each JUnit test used its own generated schema. No existing database/service was altered. A new random local database password was kept in ignored test workspace storage and process environment.
- `gradlew.bat -PserverOnly=true :server:test :server:installDist --no-daemon --console=plain` passed. **16 tests, zero failures/errors/skips**: 13 service/real-PostgreSQL cases and three Ktor HTTP/WebSocket cases. The recorded final suites took 17.490 and 6.667 seconds; the build took 48 seconds.
- **17 domain tests passed**, including the existing 100,000 generated-ticket cases and six new custom-rule/save-migration tests. Later unchanged domain tasks reused these passing results; they are not additional executions.
- Four concurrent identical draw commands returned the same stored response and drew once. Two different draw commands with one revision produced one success and one stale-revision rejection.
- Two scheduler workers produced one call for the due interval. A delayed replacement service drew once without bursting catch-up calls. Host controls transferred after the original host lost presence; the connected successor could pause/resume the same round.
- An injected event-insert failure rolled back both the room mutation and the command receipt. Removing the injected conflict and retrying the same request succeeded.
- Two-player and maximum-size **32-player / 192-ticket** games completed all 90 calls in real PostgreSQL. The maximum-size test verified all 192 tickets were unique. The two-player test also checked custom awards, authoritative scores, a finished-round audit and rematch. These are correctness cases, not a 10-room load benchmark.
- HTTP tests checked health, strict/oversized body handling, content type, authentication, logout and invalid cursors. Two authenticated WebSocket clients received their own cards and the same durable call; a non-member stream closed without disclosing the room snapshot.
- Privacy assertions checked that initial snapshots contain no other player's ticket, private future draw order or nonce. Finished/cancelled rounds reveal the order/nonce and reproduce the initial commitment. Token storage assertions checked a hash rather than the original credential.
- Rate-limit persistence, room expiry, cleanup, readiness resets, locked/full rooms, host-only commands, frozen settings, membership removal, leave/retry, old/future event cursors and session expiry/revocation passed.
- `node tools/server-smoke.mjs` passed against the **actual Netty Java process**, outside Ktor's test host. It created two sessions, started a room, called a number, killed only its owned Java process, restarted it against the same database, recovered the same call/event, replayed the original command receipt exactly, ended the round and stopped the process. The final run after script cleanup also passed.
- Dependency SHA-256 metadata was expanded for the new pinned libraries; subsequent builds passed ordinary verification without the metadata-writing flag. A server-specific PostgreSQL CI job and restart smoke step were added. Hosted CI has **not** run.
- `:app:assembleDebug :app:lintDebug` still pass with the extended shared domain. The current lint run reports **zero errors and one KAPT/KSP migration warning**; earlier alpha lint had additional available-version notices. This does not constitute a dependency vulnerability audit.
- The current-source `:app:connectedDebugAndroidTest` regression also passed **4/4** on the dedicated Android 11/API 30 emulator. Complete offline play, recreation, family tickets and history/current-round isolation continue to work with the extended domain. Online Android integration and an actual APK-update migration are not covered by those tests.

## Evidence boundaries

No hosted endpoint, TLS ingress, production database, backup restore, rollback drill, metrics/alerts, proxy-aware abuse test, sustained load/latency measurement, native online Android journey, two physical-phone round, or independent security review has passed yet. User-requested data deletion and account/session recovery need implementation. Android custom-rule editing and online integration remain separate work.

The previously packaged `0.1.0-alpha01` APK remains an offline-only internal alpha. No new production APK/AAB, signing identity, store submission or paid infrastructure was created by this service milestone. The full goal remains active.
