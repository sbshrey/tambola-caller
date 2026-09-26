# Returning-player continuity

The online coin game must retain the same wallet when a seven-day guest session expires. It must not silently create another profile, grant another starter balance, discard owned tickets/marks, or replace pending purchase/claim IDs. This change adds automatic renewal on the same installation without a signup screen.

## Implementation

- Schema 006 adds a unique nullable device-credential hash and a monotonic session revision to the existing guest row. Existing profiles enroll while their current access token remains valid. The device credential is a separate 256-bit random secret; the server stores only its hash. Existing room protocol 4 and guest-registration response remain compatible.
- The app encrypts the device credential with its existing Android Keystore/AtomicFile store before enrollment. The credential is not sent to room, wallet or game-command endpoints. Short-lived access tokens remain valid for seven days; the server decides expiry, avoiding dependence on the phone clock.
- On an authenticated request rejection, the client persists a new random access token and expected revision before asking the server to rotate. Concurrent renewal callers share one coordinator. A lost response retries the same token/revision; stale requests cannot replace a newer session. A replacement is saved only if player, token and revision match. Updates preserve newer marks, wallet snapshots and pending game operations.
- Renewing never writes to the coin ledger. Profile-level purchase/refund receipts remain valid after a token change. Profiles with enabled device credentials survive session-expiry cleanup; unenrolled/revoked profiles retain the existing 30-day-after-expiry cleanup policy.
- Explicit logout durably records revocation in the independent journal before clearing the device hash and current session. Profile deletion removes the wallet and device proof. Both access and renewal check the journal before accepting restored credentials.
- Before deletion, the app resolves any interrupted session rotation, then freezes that token for the deletion request and its retries. Background wallet/stream activity cannot rotate the proof while deletion is pending. An expired legacy token still has the existing deletion-only path.

## Verification

Server acceptance covers 90-day wallet retention, exact renewal replay after service reconstruction, concurrent identical and competing requests, enrollment replacement/collision rejection, SQL rollback, original purchase-receipt replay, logout, strict HTTP/no-store boundaries, restricted database roles, and denial of deleted credentials after a real primary `pg_dump`/`pg_restore` while retaining the independent journal.

Client acceptance covers durable intent before HTTP, concurrent renewal, response loss and coordinator reconstruction, failed storage, wrong-profile responses, preservation of concurrent marks/purchases, and stable deletion proofs. Native acceptance injects an initial wallet 401 in the isolated loopback proxy, drops a genuinely committed renewal response, recreates the Activity, reconnects the same paid game, then drops/retries deletion confirmation. This is distinct from a physical phone, app process-death test, or waiting seven calendar days.

Completed checks: all 207 JVM tests, final Android builds/unit checks/lint, the native renewal/deletion test and both native coin-flow regressions passed. [The archived evidence](../full-game/reviews/device-sessions-2026-09-27/README.md) records exact binary hashes and scope. The native app retained six tickets, a manually placed mark, its original profile and a 900-coin balance after the dropped rotation response; only one session revision was issued.

The follow-on durable-logout candidate passed 38 focused integration checks, then the complete 125-test server suite with the 90 unchanged domain/client/app unit results up to date. Android build/lint and real-process restricted-role HTTP enrollment/logout/renewal rejection also passed. Its real primary restore preserves peer gameplay and wallet history while rejecting signed-out access. See [exact evidence and boundaries](../full-game/reviews/session-revocations-2026-09-27/README.md).

## Remaining boundaries

This provides continuity on the same installation. Reset, uninstall, lost Keystore or lost device can still lose access; optional recovery on another installation remains work. Already-expired legacy profiles that never enrolled cannot acquire a new device credential without another ownership proof; they are retained for explicit deletion rather than silently replaced.

The next recovery change adds journal migration 003 with append-only logout records sharing the existing commit-ordered head and primary replay cursor. Replay revokes access without deleting the profile, wallet or shared history; normal revoked-profile retention still applies. Lost logout responses receive an unauthorized retry once the durable revocation exists. Existing deletion confirmation semantics remain separate. Validation and deployment status are recorded in [revocation evidence](../full-game/reviews/session-revocations-2026-09-27/README.md). Do not restore the installed host merely to test this; use disposable databases and preserve the live journal.

The installed alpha16 host/APK remain separate from this candidate until a clean committed upgrade with paired backups and matching native validation is completed. Physical-phone Wi-Fi, optimized frame times, identity recovery, native refill, and the other gates in `ONLINE_COIN_GAME_PLAN.md` remain required for release.
