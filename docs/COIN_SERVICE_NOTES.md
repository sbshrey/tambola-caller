# Coin service integration notes

Current implementation boundary: schema 004, `CoinLedger`, authenticated wallet/refill endpoints and starter-wallet creation are implemented and passed focused PostgreSQL, HTTP and restricted-role tests. The installed Wi-Fi host still runs the tested manual-game checkpoint; it has not received schema 004 or the wallet endpoints. Purchases, settlement wiring, matchmaking and wallet UI remain to implement.

## Transaction design

- Preserve lock order: guest, room, wallet. Serialize spends/refills with `FOR NO KEY UPDATE` on the wallet. Positive settlement credits use append-only inserts and FK key-share locks, which remain compatible with spending. Two rooms may pay overlapping players in different orders without acquiring conflicting wallet locks.
- Balance is the sum of immutable ledger entries; its revision is their count. Unique `(player_id, entry_key)` identities prevent repeated debits/credits. A repeated settled key with a different amount is an invariant violation. A deleted profile's wallet must never be recreated by a delayed payout.
- Use `RoomRecord`'s per-player purchased quantities as the authoritative reservation state under the room lock; avoid maintaining a second mutable quantity table. Apply quantity deltas and room changes in the same database transaction. Use room revision/player identity in debit/refund keys. The existing command receipt commits in that same transaction.
- Reconcile reservations only when the previous room phase is LOBBY. Removing a buyer, abandoning or expiring an unstarted lobby refunds once. Transitioning from FINISHED into a new lobby must not refund a previous round's already-settled purchase.
- Freeze `CoinPool` and independently chosen ticket counts at sales closure. Pass those quantities to domain Round version 5. Wire projection must carry public quantities and only the actor's ticket contents. Never expose marks, proofs or unrevealed draw order.
- At every committed round transition, append cumulative `CoinPool.allocations` with unique round/allocation keys. The engine excludes unsettled current-call ties. Credits become spendable only after the call window closes. Completion/cancellation returns only the unawarded pool. The ledger skips wallets whose profiles were deleted.
- Wallet snapshots can be older inside replayed command receipts. The client should retain the highest wallet revision for the same identity, independently of room revision. Preserve pending purchases/refill operation IDs before sending; a retry must retain the original ID and intent.

## Minimal online flow

1. One-time nickname/avatar or a generated guest identity, then 1–6 tickets and Play. No offline mode, tutorial or badge navigation in the entry flow.
2. An idempotent match request should atomically join an accepting paid lobby and buy the chosen quantity. A short server countdown closes sales. Readiness follows the purchase, avoiding an extra Ready/Start step in this mode.
3. Prefer a waiting real-player lobby. Fill remaining seats with explicitly labelled computers at closure. Freeze their contributions/quantities and the resulting prize schedule before the first call. Real arrivals after closure join the next lobby.
4. An already purchased active game resumes instead of buying another entry. A finished game's Play Again is a fresh purchase/commitment. Disallow arbitrary host pauses, manual calls and casual cancellation in shared coin games.
5. Show spendable coins, ticket cost, countdown/pool and prize values concisely. Keep the tested two-ticket layout, arrows and per-ticket picker. Verified claims may celebrate immediately while their coin share remains provisional until closure.

## Remaining acceptance

Cover purchases/refunds and exact receipts across concurrency, DB rollback, restart, profile deletion and expiry; partial quantities; 6/7/8 prize schedules; ties and ranked houses; full-pool conservation including computer allocations; stale responses; reconnect; and two real clients. Update server/client protocol together when new room fields/actions are added.

Before upgrading the installed host: build a committed candidate, back up both stores, verify no active round is disrupted, apply migrations with owner credentials, reapply reviewed runtime grants, then start restricted runtime credentials and confirm readiness plus a real write/read. An older binary rejects newer migration versions, so rollback cannot be assumed to mean merely copying old jars. Keep the live deletion journal across restore operations.

Anonymous identity currently uses the existing expiring guest session. A release must settle its refresh/recovery behavior so a returning player's coin balance is not silently replaced by a new guest. This is a release requirement, not permission to add a signup wall.
