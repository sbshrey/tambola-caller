# Private room service: local development candidate

Ktor/JDK 17 service backed by PostgreSQL. This is a tested local development implementation, **not a hosted production deployment**. The current alpha12 APK connects through an explicitly configured endpoint; its packaged debug default is loopback for emulator testing. See [alpha12 validation](../ALPHA12_VALIDATION.md), [runtime dependency review](../DEPENDENCY_REVIEW.md) and [service recovery validation](RECOVERY_VALIDATION.md). Use the current source build or `releases/service-security-2026-09-26/Tambola-service.zip` under `full-game/` for the updated Netty/Logback runtime.

## Run and test

From `full-game/`, build with no Android SDK requirement:

```powershell
.\gradlew.bat -PserverOnly=true :server:installDist
```

Supply `TAMBOLA_DATABASE_URL` (PostgreSQL JDBC URL), `TAMBOLA_DATABASE_USER`, and `TAMBOLA_DATABASE_PASSWORD` through the process environment/secret manager. Also supply `TAMBOLA_DELETION_DATABASE_URL`, `TAMBOLA_DELETION_DATABASE_USER` and `TAMBOLA_DELETION_DATABASE_PASSWORD` for the independently retained deletion journal. Run `server.bat --migrate` separately with owner credentials, then apply the [runtime grants](DATABASE_PERMISSIONS.md). Ordinary startup uses restricted credentials, verifies exact migrations/permissions and separate storage, and replays deletion intents before opening HTTP. The journal must stay current when primary room data is restored. See the [configuration and restore runbook](BACKUP_RECOVERY.md), including controlled cutover and rollback constraints.

Do not place credentials in command arguments, source, APK resources or logs. Run `server/build/install/server/bin/server.bat` on Windows, or the adjacent `server` script on Linux. Without a journal, startup is permitted only with explicit `TAMBOLA_LOCAL_DEVELOPMENT=true`, a loopback listener and a primary URL exactly matching `jdbc:postgresql://127.0.0.1:<port>/tambola_test` or `tambola_dev`. This fixture exception provides no deletion-after-restore protection.

Default listener: `127.0.0.1:8080`. `PORT` changes the port. A non-loopback `TAMBOLA_BIND_HOST` requires `TAMBOLA_TLS_PROXY=true`; this is a deployment acknowledgement, **not TLS implementation**. A correctly configured TLS reverse proxy is still required before exposing the service.

The [operations runbook](OPERATIONS.md) covers the non-root container, liveness/readiness, restricted metrics, alert rules, resource limits and incident response. Configure a separate random `TAMBOLA_METRICS_TOKEN` for protected `/internal/metrics`; without it that endpoint is disabled. Runtime readiness now also requires recent worker progress, so available database connections cannot hide failed automatic calling or cleanup.

Integration tests require explicit `TAMBOLA_TEST_DATABASE_URL`, `TAMBOLA_TEST_DATABASE_USER`, and `TAMBOLA_TEST_DATABASE_PASSWORD`. The backup/permission drills require the loopback URL above and `TAMBOLA_PG_BIN` pointing to PostgreSQL 16 client binaries (for example `C:\Program Files\PostgreSQL\16\bin`). The isolated test administrator needs schema creation, CREATEDB and CREATEROLE privileges. Tests create fresh owned schemas or databases/roles and remove only their owned resources afterward. No database fallback and no silently skipped integration tests exist.

```powershell
.\gradlew.bat -PserverOnly=true :domain:test :client:test :server:test :server:installDist
node tools/server-smoke.mjs
node tools/server-deletion-recovery.mjs
node tools/server-permissions-smoke.mjs
```

The smoke script requires Node 22+, a loopback JDBC URL for `tambola_test`, and the built distribution. It explicitly uses local-development mode, starts an owned Java child process on a free loopback port, registers two fictional players, calls a number, kills that process, restarts it, verifies the saved state/event/command receipt, ends the round, and stops the owned server. Session secrets stay in process memory. The small synthetic room remains in the test database until retention cleanup; it does not write to a production database.

The separate deletion-recovery process drill creates three fresh owned databases, runs the real service with journal enforcement and explicit local fixture privileges, dumps/restores only the primary database, rejects a mismatched journal before listening and verifies recovery with the current journal. It removes its owned databases/processes and temporary dump, retaining safe evidence JSON. The permission drill independently exercises the production startup path with four non-superuser owner/runtime roles, local mode false, actual SQL denial checks and a restarted two-player game. See [permission setup and scope](DATABASE_PERMISSIONS.md).

## Protocol envelope 2, `/v1` routes

Alpha08–alpha12 clients use protocol 2, including immutable round-player avatars; earlier clients do not understand these fields. Stored older rounds/receipts remain readable by the new service/client. Current schemas are primary migration 003 and journal migration 002. See [save/protocol compatibility](../AVATARS.md), [permission upgrades](DATABASE_PERMISSIONS.md) and [recovery constraints](BACKUP_RECOVERY.md) before an upgrade or rollback.

All bodies and responses use strict JSON. Session credentials are opaque bearer tokens in the `Authorization` header; never put them in a URL. A guest has one active token, valid for seven days, stored as SHA-256 only. Logging out revokes it. There is no account/password recovery yet.

| Endpoint | Purpose |
| --- | --- |
| `GET /health/live` / `GET /health/ready` | Process liveness / recent successful worker plus database and recovery readiness |
| `GET /internal/metrics` | Private fixed-label Prometheus metrics; separate monitoring bearer secret, disabled unless configured |
| `GET /invite/{code}` | Optional bilingual invitation/install page; no room lookup or registration |
| `GET /.well-known/assetlinks.json` | Optional public Android package/signing association |
| `POST /v1/guests` | Name (1–40 characters), avatar (0–7); returns guest ID and bearer token |
| `POST /v1/guests/me/logout` | Revoke the current token; this is not data deletion |
| `POST /v1/guests/me/delete` | Delete the authenticated profile and redact its stored profile fields; retry the original UUID to confirm |
| `POST /v1/rooms` | Create a private lobby with UUID `id` and frozen-at-start `options` |
| `POST /v1/rooms/{code}/join` | Join an unlocked lobby; existing members can reconnect |
| `GET /v1/rooms/{code}?after=N` | Personalized snapshot plus events after a revision |
| `POST /v1/rooms/{code}/commands` | UUID `id`, `expectedRevision`, typed `action` |
| `WS /v1/rooms/{code}/events?after=N` | Authenticated personalized snapshot stream; inbound messages are revision acknowledgements only |

Canonical DTOs are in `protocol/.../Rooms.kt`. Actions are `avatar`, `ready`, `configure`, `lock`, `start`, `draw`, `pause`, `resume`, `end`, `rematch`, `remove`, `leave`, encoded by the `type` discriminator. `avatar` changes the authenticated lobby member's choice (0–7), resets only their readiness and updates their future-room profile in the same transaction. Invalid actions/settings fail with a safe error code. Commands that race a newer room revision return `409 stale_revision`; clients refresh before issuing a new command. Retrying an uncertain command must reuse **the identical UUID, revision and body**. A matching receipt returns the original response, even if later room revisions exist. Clients must never replace newer visible state with an older retry response. Creation retries return the current personalized snapshot of the originally created room.

Two to 32 humans can play, with one to six tickets each. Everyone must be ready and recently connected before the host starts. Configuration resets readiness. Starting freezes tickets/rules. The host can pause/resume/end or call numbers in a manual room. Automatic rooms use only server-owned scheduling. No client claim/mark can award points. Standard/custom rules, ties and scoring use the same domain engine as offline play.

Ordinary membership changes are limited to the lobby/finished room; explicit profile deletion can remove membership during play. A player may disconnect during play and rejoin with the same stored session, while their tickets remain eligible. Ending a round records cancellation, rather than pretending it completed. Rematch returns to the lobby and clears readiness. Removing a lobby member revokes their read/stream access; knowing a room code is insufficient to read its state.

## Consistency, privacy and recovery

- One room row is the transactional aggregate. Mutations lock it and atomically commit state, a command receipt and a public event. Receipts are scoped by room, authenticated actor and command UUID; a reused UUID with a different request is rejected.
- The worker scans bounded batches with `FOR UPDATE SKIP LOCKED`. A short database transaction owns each transition, so there is no separate renewable in-memory lease. Multiple workers and command writers serialize on the same row. Persisted `nextDrawAt` survives process failure; delayed workers draw once and schedule from the current time, without a catch-up burst.
- Public events contain revision, event kind, timestamp and round ID. They also serve as a durable outbox: a crash after commit cannot lose the event. The last 1,000 events remain replayable. Missing/future/too-old cursors require a fresh snapshot. WebSocket connections poll committed state every 500 ms and recheck authentication/membership. Each connection therefore uses about 120 of the profile's 300 reads per minute; multiple connections share that quota. See the [ten-room capacity measurements](CAPACITY_VALIDATION.md) for tested latency/resource limits and outstanding hosted acceptance.
- Public snapshots contain only the viewer's tickets, calls, awards and scores. The private domain `Round` is never a response DTO. Future draw order and nonce remain private until completion/cancellation.
- The pre-round commitment is SHA-256 of UTF-8 `tambola-draw-v1\n<roundId>\n<nonce>\n<comma-separated draw order>`. At the end, order and nonce allow a client to check that the order did not change. This establishes consistency with the published commitment; it does not independently prove an unbiased server.
- Activity updates presence at most every 15 seconds. After 45 seconds without activity, host controls move to the earliest joined connected member (ID breaks timestamp ties). A former host does not automatically reclaim controls. Automatic calling continues even if all players disconnect.
- Rooms close 24 hours after creation. Room data, receipts, events and finished-round audits are deleted 30 days after that expiry. Expired guest credentials/profiles are deleted after 30 days. Rate buckets are short lived. Explicit [profile deletion](PROFILE_DELETION.md) removes access and redacts stored profile fields while preserving shared game records; its confirmation expires after 30 days. Independently retained suppression intents have no automatic pruning in this version. Startup replays them after primary restoration; provider backup independence, retention and restore acceptance remain deployment work. Logout does not claim to erase history.

## Resource limits and remaining release work

Public invitation pages are optional until `TAMBOLA_PUBLIC_ORIGIN` is configured. The domain must match the native app's HTTPS origin; signing fingerprints and an installation destination are separate configuration. See [invitation behavior and hosted acceptance](../INVITES.md).

JSON request bodies are capped at 32 KiB with a 10-second read timeout; WebSocket input frames are capped at 1 KiB. Custom rules have bounded selectors/groups/counts. SQL uses prepared parameters, 5-second lock/pool timeouts and a 10-second statement timeout. Outbound sends have a 10-second deadline and no unbounded application event queue.

Persisted minute buckets limit guest creation to 60 per socket peer address and authenticated create/join/command/read requests to 10/20/180/300 per profile. A profile can own at most five unexpired open rooms. Authentication happens before creating profile rate buckets. The service does not trust forwarded address headers; proxy-aware rate enforcement and connection/body limits must be configured and tested with the actual ingress provider. These initial quotas are not a complete abuse/DoS defense.

Native Android session storage/lobby/game/reconnect flows and explicit profile deletion are implemented in the [client](../client/README.md). Local deletion-after-restore checks and the ten-room/320-client manual-game capacity workload pass with the real service process. Migration/runtime separation and checked grants are implemented; [permission validation](PERMISSIONS_VALIDATION.md) records local evidence. Metrics, worker-aware readiness, alerts and a constrained runtime image now have [local operations checks](OPERATIONS_VALIDATION.md). Still required: invite links, production identity/recovery decisions, hosting/TLS/secrets and deployed role/rotation/alert-delivery acceptance, independently durable journal and provider restore/retention acceptance, updated user-facing recovery-data disclosure, dependency/advisory review, hosted/broader-fault load and large-history deletion measurements, fault/rollback drills, two physical-phone acceptance and deployment validation. No cloud resources have been provisioned. Do not advertise this candidate as production ready.

The opt-in `:server:loadTest` task runs the full 320-client/1,920-ticket workload on fresh owned databases, requires every call/result to reach every client and enforces a conservative p95 delivery gate. It is separate from ordinary JUnit execution. [Capacity instructions](CAPACITY.md) describe fixture permissions, measurement definitions and safe cleanup.

See [service validation](VALIDATION.md) and the repository execution ledger for observed evidence.

Protocol v1 native-client additions: `PublicRound.players` retains the immutable round roster, while `winningTickets` contains owner IDs and ordinals only for already awarded tickets. Neither field exposes another player's card numbers. Defaults permit decoding older persisted receipts; no previously released APK consumed this room protocol.

Retained-history deletion now streams records within its atomic transaction, and background replay defers work already locked by a live operation. See [the memory/contention regression, tests and evidence](HISTORY_DELETION_VALIDATION.md). No schema, grant or protocol change is required.

The opt-in [automatic recovery fixture](AUTOMATIC_RECOVERY.md) completes a full 32-player game across two service processes, lost responses, host succession, slow native consumption and service outages. [Exact validation](AUTOMATIC_RECOVERY_VALIDATION.md) records the passing candidate and remaining hosted, Android-device, capacity and soak limits.

The separate [automatic capacity/soak fixture](AUTOMATIC_SOAK.md) keeps ten rooms and 320 native clients connected through nine games and rematches. It records per-game correctness, delivery, retained-heap checkpoints and cleanup; only an actual complete run establishes its stated workload.
