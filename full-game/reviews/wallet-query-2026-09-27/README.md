# Remove an unused intermediate wallet read

`CoinLedger.change` returned a wallet snapshot that its only production caller discarded. It now completes the ledger write without that extra read; the room response still reads the final wallet after reconciliation. Locking, balance checks and idempotency are unchanged.

All **57** targeted PostgreSQL tests passed: coin ledger/matchmaking, HTTP, device sessions, revocation, backup restoration and actual restricted runtime roles. The installed service and its databases were unchanged.

Two isolated 320-player purchase waves with immediate streams, eight-player tables, exact retries and cancellation/refund checks passed for both the installed baseline runtime (`f2b39e5b…`) and candidate (`7dd0d95c…`). Each used a fresh service/database pair; the second wave reused its clients and service. Diagnostic instrumentation was disabled. All owned processes and databases were independently confirmed absent afterwards.

| Runtime | First-wave purchase p95 | Second-wave purchase p95 |
|---|---:|---:|
| Installed baseline | 3,165.576 ms | 1,200.775 ms |
| Candidate | 3,275.892 ms | 1,240.828 ms |

This single sequential comparison demonstrates **no latency improvement** and neither condition meets the subsecond target. The change removes unnecessary work but does not solve serialized matchmaking contention. It has **not been deployed**. These cancelled purchase waves are not completed-round, physical-network or capacity acceptance (`capacityAcceptance:false`). Full identities, timing definitions, receipt checks and cleanup are retained in [baseline](baseline.json), [candidate](candidate.json), [regression validation](regression-validation.json) and [the load guide](../../server/COIN_LOAD.md).
