# Online Bingo backend checkpoint

This is source work for the multi-game goal, not a deployed server or published APK.

## Implemented

- Separate 75-ball matchmaking, room and command contracts at `/v1/bingo`, using the existing authenticated profile and coin ledger. Bingo invitation codes have a `B-` prefix; legacy Tambola DTOs remain unchanged.
- Public countdowns progressively fill a varying 30–50-player roster, with one to six cards per player. Friends tables start explicitly with at least two people.
- Persisted private-card projections, revision checks, durable idempotency receipts, server-owned draws, marks and claims. Future calls and other players' cards are absent from active snapshots.
- Versioned 20/20/20/40 coin pools. Same-call ties settle when the category closes. Unawarded amounts return per purchased card on completion/cancellation. Computer allocations are not credited to human wallets.
- Existing worker, deletion/redaction and restricted runtime grants include the new tables. Migration 010 is additive.

## Validation scope

The isolated PostgreSQL 16 database runs on localhost port 55432. The live host database on port 55433 has not been migrated by this checkpoint.

Domain tests cover provisional ties, exact conservation, cancellation refunds and invalid policy/card counts. Server integration tests cover concurrent purchase/replay, leave refunds, cross-game queue guards, progressive population, private cards, service restart, missed-countdown recovery, profile deletion and a full two-person friends round through final-call claims and settlement.

Passed on 2026-09-30:

- 14 Bingo domain tests, including three coin-pool tests.
- Seven PostgreSQL Bingo service tests, including receipt-write failure rollback and retry.
- Two existing expanded Tambola matchmaking tests and ten existing runtime-privilege tests.
- One additional Bingo-specific restricted-runtime test covering purchase, worker access, durable profile deletion/redaction, refund and cleanup.
- Server Kotlin compilation and Git whitespace validation.

The broad regression invocation completed in 3m 24s. After adding the rollback and restricted-runtime Bingo cases, the final focused invocation passed in 43s. No test failures or errors were reported.

Remaining acceptance includes HTTP/client integration, native online Bingo and reconnection, broader compatibility checks, live deployment and a same-signer APK/update acceptance run. The multi-game goal remains active.
