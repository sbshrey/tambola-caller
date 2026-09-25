# Private room service: local development candidate

Ktor/JDK 17 service backed by PostgreSQL. This is a tested local development implementation, **not a hosted production deployment**. The native alpha03 APK connects to it through an explicitly configured endpoint; its packaged debug default is loopback for emulator testing. See [alpha03 validation](../ALPHA03_VALIDATION.md).

## Run and test

From `full-game/`, build with no Android SDK requirement:

```powershell
.\gradlew.bat -PserverOnly=true :server:installDist
```

Supply `TAMBOLA_DATABASE_URL` (PostgreSQL JDBC URL), `TAMBOLA_DATABASE_USER`, and `TAMBOLA_DATABASE_PASSWORD` through the process environment/secret manager. Do not place credentials in command arguments, source, APK resources or logs. Run `server/build/install/server/bin/server.bat` on Windows, or the adjacent `server` script on Linux. Startup applies the checked, immutable SQL migration under a PostgreSQL advisory transaction lock. Use a dedicated database and appropriately restricted database credentials.

Default listener: `127.0.0.1:8080`. `PORT` changes the port. A non-loopback `TAMBOLA_BIND_HOST` requires `TAMBOLA_TLS_PROXY=true`; this is a deployment acknowledgement, **not TLS implementation**. A correctly configured TLS reverse proxy is still required before exposing the service.

Integration tests require explicit `TAMBOLA_TEST_DATABASE_URL`, `TAMBOLA_TEST_DATABASE_USER`, and `TAMBOLA_TEST_DATABASE_PASSWORD`. The URL must name `tambola_test`; the test role needs schema creation privileges in that isolated database. Every test creates a random schema and drops only that schema afterward. No database fallback and no silently skipped integration tests exist.

```powershell
.\gradlew.bat -PserverOnly=true :domain:test :server:test :server:installDist
node tools/server-smoke.mjs
```

The smoke script requires Node 22+, a loopback JDBC URL for `tambola_test`, and the built distribution. It starts an owned Java child process on a free loopback port, registers two fictional players, calls a number, kills that process, restarts it, verifies the saved state/event/command receipt, ends the round, and stops the owned server. Session secrets stay in process memory. The small synthetic room remains in the test database until retention cleanup; it does not write to a production database.

## Protocol v1

All bodies and responses use strict JSON. Session credentials are opaque bearer tokens in the `Authorization` header; never put them in a URL. A guest has one active token, valid for seven days, stored as SHA-256 only. Logging out revokes it. There is no account/password recovery yet.

| Endpoint | Purpose |
| --- | --- |
| `GET /health/live` / `GET /health/ready` | Process / database readiness |
| `POST /v1/guests` | Name (1–40 characters), avatar (0–7); returns guest ID and bearer token |
| `POST /v1/guests/me/logout` | Revoke the current token; this is not data deletion |
| `POST /v1/rooms` | Create a private lobby with UUID `id` and frozen-at-start `options` |
| `POST /v1/rooms/{code}/join` | Join an unlocked lobby; existing members can reconnect |
| `GET /v1/rooms/{code}?after=N` | Personalized snapshot plus events after a revision |
| `POST /v1/rooms/{code}/commands` | UUID `id`, `expectedRevision`, typed `action` |
| `WS /v1/rooms/{code}/events?after=N` | Authenticated personalized snapshot stream; inbound messages are revision acknowledgements only |

Canonical DTOs are in `protocol/.../Rooms.kt`. Actions are `ready`, `configure`, `lock`, `start`, `draw`, `pause`, `resume`, `end`, `rematch`, `remove`, `leave`, encoded by the `type` discriminator. Invalid actions/settings fail with a safe error code. Commands that race a newer room revision return `409 stale_revision`; clients refresh before issuing a new command. Retrying an uncertain command must reuse **the identical UUID, revision and body**. A matching receipt returns the original response, even if later room revisions exist. Clients must never replace newer visible state with an older retry response. Creation retries return the current personalized snapshot of the originally created room.

Two to 32 humans can play, with one to six tickets each. Everyone must be ready and recently connected before the host starts. Configuration resets readiness. Starting freezes tickets/rules. The host can pause/resume/end or call numbers in a manual room. Automatic rooms use only server-owned scheduling. No client claim/mark can award points. Standard/custom rules, ties and scoring use the same domain engine as offline play.

Membership changes are limited to the lobby/finished room. A player may disconnect during play and rejoin with the same stored session, while their tickets remain eligible. Ending a round records cancellation, rather than pretending it completed. Rematch returns to the lobby and clears readiness. Removing a lobby member revokes their read/stream access; knowing a room code is insufficient to read its state.

## Consistency, privacy and recovery

- One room row is the transactional aggregate. Mutations lock it and atomically commit state, a command receipt and a public event. Receipts are scoped by room, authenticated actor and command UUID; a reused UUID with a different request is rejected.
- The worker scans bounded batches with `FOR UPDATE SKIP LOCKED`. A short database transaction owns each transition, so there is no separate renewable in-memory lease. Multiple workers and command writers serialize on the same row. Persisted `nextDrawAt` survives process failure; delayed workers draw once and schedule from the current time, without a catch-up burst.
- Public events contain revision, event kind, timestamp and round ID. They also serve as a durable outbox: a crash after commit cannot lose the event. The last 1,000 events remain replayable. Missing/future/too-old cursors require a fresh snapshot. WebSocket connections poll committed state once a second and recheck authentication/membership; this is an initial transport implementation, not a proven production latency/load result.
- Public snapshots contain only the viewer's tickets, calls, awards and scores. The private domain `Round` is never a response DTO. Future draw order and nonce remain private until completion/cancellation.
- The pre-round commitment is SHA-256 of UTF-8 `tambola-draw-v1\n<roundId>\n<nonce>\n<comma-separated draw order>`. At the end, order and nonce allow a client to check that the order did not change. This establishes consistency with the published commitment; it does not independently prove an unbiased server.
- Activity updates presence at most every 15 seconds. After 45 seconds without activity, host controls move to the earliest joined connected member (ID breaks timestamp ties). A former host does not automatically reclaim controls. Automatic calling continues even if all players disconnect.
- Rooms close 24 hours after creation. Room data, receipts, events and finished-round audits are deleted 30 days after that expiry. Expired guest credentials/profiles are deleted after 30 days. Rate buckets are short lived. Backup retention and explicit user data deletion are still deployment/release work; logout does not claim to erase history.

## Resource limits and remaining release work

JSON request bodies are capped at 32 KiB with a 10-second read timeout; WebSocket input frames are capped at 1 KiB. Custom rules have bounded selectors/groups/counts. SQL uses prepared parameters, 5-second lock/pool timeouts and a 10-second statement timeout. Outbound sends have a 10-second deadline and no unbounded application event queue.

Persisted minute buckets limit guest creation to 60 per socket peer address and authenticated create/join/command/read requests to 10/20/180/300 per profile. A profile can own at most five unexpired open rooms. Authentication happens before creating profile rate buckets. The service does not trust forwarded address headers; proxy-aware rate enforcement and connection/body limits must be configured and tested with the actual ingress provider. These initial quotas are not a complete abuse/DoS defense.

Native Android session storage/lobby/game/reconnect flows are implemented in the [client](../client/README.md); the alpha03 host and guest emulator journeys pass against this service. Still required: invite links, data deletion, production identity/recovery decisions, hosting/TLS/secrets, least-privilege migration/runtime roles, metrics/alerts, backup/restore, dependency/advisory review, 10-room concurrent load and latency measurements, fault/rollback drills, two physical-phone acceptance and deployment validation. No cloud resources have been provisioned. Do not advertise this candidate as production ready.

See [service validation](VALIDATION.md) and the repository execution ledger for observed evidence.

Protocol v1 native-client additions: `PublicRound.players` retains the immutable round roster, while `winningTickets` contains owner IDs and ordinals only for already awarded tickets. Neither field exposes another player's card numbers. Defaults permit decoding older persisted receipts; no previously released APK consumed this room protocol.
