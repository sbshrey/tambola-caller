# Migration and runtime permission validation — 26 September 2026

This service candidate separates schema migration from serving and enforces restricted database roles at startup. It was tested on local Windows, Microsoft JDK 17.0.14.7 and isolated PostgreSQL 16.9 at loopback port 55432. No provider deployment or hosted CI run is claimed. [Database setup](DATABASE_PERMISSIONS.md) explains the required upgrade and grant procedure.

Exact service JAR SHA-256: `a0432f85f84af8bd8491a4cd2d4f151d9777a819393f74207728177854ded03d`. The separate `releases/service-permissions-2026-09-26/` package records the committed source revision, distribution hash, safe evidence and file manifest. Primary migrations remain 001–003; journal migration 002 adds the head guard. Protocol 2, round format 3 and alpha10 APK SHA-256 `69ce9cacdb4bc95496fedc61749c857dd4583b18dd5111b9938846ef0583553b` are unchanged.

## Automated service suite

`:server:test :server:installDist` passed in **3m 23s**, with **50 tests, zero failures, errors or skips**. All service cases executed; domain/client dependencies were up to date and their tests were not newly executed. `:server:distZip` then passed in 11 seconds with the tested JAR unchanged.

| Suite | Cases | Recorded test seconds |
| --- | ---: | ---: |
| Avatar | 3 | 4.946 |
| Backup restore | 1 | 4.820 |
| Independent deletion journal | 10 | 19.311 |
| HTTP | 4 | 5.501 |
| Native protocol client | 1 | 1.961 |
| Profile deletion | 7 | 9.506 |
| Room service | 15 | 22.106 |
| Runtime privileges | 7 | 111.224 |
| Startup configuration | 2 | 0.001 |

The new role tests use actual fresh LOGIN roles and real authenticated PostgreSQL connections. They verify gameplay/rematch/archive/redaction/replay/retention cleanup, retained suppression, denied schema/temp/registry changes, denied journal UPDATE/DELETE/TRUNCATE/ALTER/DROP and trigger disabling, head rewind/skip/missing-intent rejection, excess or missing grants, table and column delegation, role membership, and cross-database connection denial. Missing/modified/future migrations fail without repair; upgrading an existing journal preserves identity, intents and sequence. Explicit local fixture mode checks both database targets and rejects remote/arbitrary/query-string targets.

## Final binary process checks

`server-permissions-smoke.mjs`, run **1d61a528f0a643c1**, passed all seven stages with local-development mode **false**:

1. Unmigrated runtime startup failed before listening and created no journal tables.
2. A separate `--migrate` job with two non-superuser owners exited successfully without a listener; the exact grant templates were applied as those owners.
3. Owner credentials were rejected for serving.
4. An excessive journal column grant was rejected before listening, then explicitly revoked.
5. Two players created, joined, readied, started and called a number using restricted runtime credentials.
6. The service restarted from PID **17316** to **1132**. Deleting the host retained peer cards/calls, redacted the shared identity, transferred controls, confirmed the identical retry and let the peer draw/end.
7. Actual SQL attempts to erase/change deletion records, roll back the journal head, change migrations or create a table were denied.

The driver removed its two databases, four roles and owned Java children. Passwords remain in memory/environment/SQL stdin; evidence contains no tokens, raw databases or child-process output. An earlier successful intermediate proof **b8fcacbd40164dd0** used another JAR and is preserved only as intermediate evidence, not acceptance of this binary.

The ordinary `server-smoke.mjs` restart/receipt/event/private-ticket checks also passed. The independent backup process drill **b9d6596229d6427a** passed on the same final JAR: seed PID **12916**, recovered PID **23436**, primary archive **18,664 bytes**, restored deleted identity demonstrated before replay, wrong journal rejected before listening, current-journal replay and surviving peer continuation verified. Owned databases/processes/dump were removed. This restore driver uses explicit privileged **local fixture mode**, so it proves recovery behavior, not provider restore under restricted credentials.

## Evidence boundaries

The permission suite and final process probes passed without a failing acceptance rerun. Earlier focused permission tests passed before the final audit refinements. Source-edit attempts rejected by the patch tool did not change source or produce accepted binaries. No Android build/device test was needed for this server-only change; existing alpha10 evidence remains separate.

The 320-client measurement at source `4192a84fbb3c622971dab45c7faa4faf377f2459` remains **historical capacity evidence**, with its own JAR hash; it was not rerun for this permission candidate. Ordinary game polling is unchanged. Hosted latency, automatic-call/fault/soak behavior, large-history deletion, permission drift monitoring, provider administrator/network controls, real credential rotation, restricted-role backup restore/cutover, independent journal durability/PITR/retention, and physical-phone acceptance remain open. Production signing and updated in-app recovery-data disclosure are also outstanding. This is a verified local milestone, not completion of the production release goal.
