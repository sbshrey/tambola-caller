# Transaction timeout setup — PostgreSQL regression and burst comparison

27 September 2026. `Database.transaction` now applies the same five-second lock timeout and ten-second statement timeout with one `SELECT set_config(..., true)` instead of two separate `SET LOCAL` round trips. The settings remain transaction-local. No pool size, authentication, receipt, wallet, allocation-lock or room-filling policy changed.

`DatabaseTimeoutTest` exercised read-only/writable transactions, commit/rollback, both configured values and reset to session defaults while retaining the same physical connection. The initial selected suite ran 73 tests: 70 passed, and three backup tests failed because the invocation omitted `TAMBOLA_PG_BIN`. After configuring the already-installed PostgreSQL tools, all 16 tests in the three affected classes passed. There is successful evidence for all **73 unique tests**; this was not a single clean 73-test invocation. [Validation](validation.json) retains both attempts, exact class counts, source hashes and the setup error.

## Like-for-like diagnostic comparison

The baseline was copied and SHA-256 verified before rebuilding, then reverified before reuse. Both runtimes ran two all-at-once 320-player waves with immediate WebSocket subscriptions, 40 full eight-player tables, 512 MiB server heap, four server processors and profiling disabled. Only `server.jar` differs between their 51-library manifests. The same client, fixture and lifecycle hashes were recorded in both runs.

| Purchase round-trip metric | Baseline | Candidate |
| --- | ---: | ---: |
| First wave p50 | 1,858.96 ms | 1,996.68 ms |
| First wave p95 | 3,506.03 ms | 3,417.35 ms |
| First wave p99 | 3,617.34 ms | 3,577.26 ms |
| Repeat wave p50 | 839.24 ms | 724.68 ms |
| Repeat wave p95 | 1,604.34 ms | 1,404.95 ms |
| Repeat wave p99 | 1,652.48 ms | 1,464.45 ms |

Both runs passed all purchase/refund balances, exact receipt replays, full-table allocation, 320 observed streams per wave and owned-resource cleanup. The first-wave baseline/candidate p95 differed by only 89 ms, while the first-wave median worsened. This single sequential comparison does **not** establish a repeatable latency improvement, and neither condition meets the subsecond target. The change removes one known database round trip per transaction; it does not solve serialized matchmaking contention.

These are cancelled purchase waves with synthetic seeded profiles on isolated PostgreSQL port 55432. They exclude registration, calls, claims, native Android, TLS, physical-network and capacity acceptance. The records explicitly retain `capacityAcceptance:false`. No installed Wi-Fi runtime was replaced.

## Artifacts

- [Baseline](baseline.json): runtime `7dd0d95c6a02345c320ed4ec018cfe3b6d1d061adeae6e5a35a39b6fe9bb3fc5`.
- [Candidate](candidate.json): runtime `dc731dd1cca93691bfe9974be677059b8ba45d7dfdf5368d7ea4621fc0154653`.
- [Initial regression transcript](regressions-initial.txt) and [corrected backup/session test transcript](regressions-corrected.txt).
- [Baseline transcript](baseline-transcript.txt) and [candidate transcript](candidate-transcript.txt).
- [Validation and source hashes](validation.json).
- [Fresh installed-host readiness and runtime identity](host-health-after.json), unchanged after both waves.

The candidate was built/tested as a source draft on repository HEAD `58911b3`; the source hashes and complete runtime manifests identify the actual tested code. It is not deployed. Preserve this distinction if packaging a later committed candidate.
