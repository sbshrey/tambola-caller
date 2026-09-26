# Coin service integration notes

Current implementation boundary: schemas 004–005, wallet/refill endpoints, transactional purchases, matchmaking, frozen pools, delayed tie settlement and refunds are implemented. Protocol 4 exposes each player's ticket quantity, coin prizes and spendable wallet without exposing other hands. The alpha16 app now opens on ticket selection, buys through a 12-second countdown, shows coin prizes beside paged tickets and provides per-ticket prize selection. Lost-response purchases survive activity recreation and retry without another debit. The installed Wi-Fi host still runs the tested manual-game checkpoint (schema 003/protocol 3); alpha16 has **not** been deployed there.

Validation: 189 distinct JVM tests across the full baseline and focused extensions, Android debug build/unit tests/lint, two native coin-flow tests against the isolated real service, and the native manual-table regression passed. See [alpha16 evidence](../full-game/reviews/coin-match-alpha16-2026-09-26/README.md). This is an internal integration checkpoint, not a signed or phone-tested release.

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

Covered: concurrent match/retry/overspend; quantity differences; exact purchase/leave receipts; rollback and retry after refill; queue refunds after outage; selected same-call ties; 6/7/8 prize schedules; full three-house settlement; whole-pool conservation; delayed computer allocations; active expiry; queued profile deletion and receipt redaction; restricted-role purchase/refund; forged HTTP prices; monotonic wallet snapshots; same-call claim revision recovery. Native checks used two independent authenticated clients (one Android UI, one HTTP client), six disjoint owned tickets, manual marking, seven-slot prize picker, refund, invalid-claim feedback and purchase-response loss with activity recreation.

Still required: guest identity refresh/recovery; active-game deletion/end settlement and restore acceptance for this economy; native successful prize/payout/results/refill/repeat-round flow over a complete round; larger text/Hindi/compact devices; new frame-time and concurrent-room latency measurements; managed host migration with both-store backup and restricted-role write/read; LAN APK/physical-phone play. The native activity reload test is not a process-death or reboot test.

Before upgrading the installed host: build a committed candidate, back up both stores, verify no active round is disrupted, apply migrations with owner credentials, reapply reviewed runtime grants, then start restricted runtime credentials and confirm readiness plus a real write/read. An older binary rejects newer migration versions, so rollback cannot be assumed to mean merely copying old jars. Keep the live deletion journal across restore operations.

Anonymous identity currently uses the existing expiring guest session. A release must settle its refresh/recovery behavior so a returning player's coin balance is not silently replaced by a new guest. This is a release requirement, not permission to add a signup wall.
