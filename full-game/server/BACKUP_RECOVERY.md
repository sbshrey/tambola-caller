# Restoring room data without restoring deleted or signed-out access

The service now requires an independently retained PostgreSQL deletion journal. A primary backup can contain old credentials and names, so restoring it alone is insufficient. Startup replays the journal into the restored primary database before opening the HTTP listener. This does not replace the hosting provider's backup, isolation, retention, encryption or restore controls.

## Storage and ordering

The primary database stores rooms, guests, ordinary receipts and migration `003_deletion_recovery.sql`: the journal identity and last applied sequence. The independent database has checked migrations 001–003, identity, committed sequence head, `profile_deletions` and `session_revocations`. Migrations 002–003 guard head advancement; runtime grants prevent modifying existing intents. A deletion row contains sequence, opaque player ID, a secret-derived confirmation hash, effective deletion time and confirmation expiry. A logout row contains only sequence, opaque player ID and original revocation time. Neither contains a display name, avatar, bearer/device secret, ticket, call order or request body.

For an authenticated deletion, the service exclusively locks the primary guest row, commits a journal intent, then atomically redacts the primary guest/rooms/audits/receipts and writes the primary confirmation. It responds only after that primary transaction commits. The effective deletion timestamp is the original durable suppression time; retries retain it. A journal-write failure cannot confirm deletion. If the journal commits but primary redaction fails or the process dies, the intent remains: authentication refuses that profile, and the original request or recovery worker can finish the redaction. A different request ID cannot replace the original intent.

Logout uses the same write-before-primary ordering. Access and session renewal reject a revoked identity immediately, even before replay. Replay restores the original revocation time, expires access and clears its device hash without deleting/redacting its wallet or shared history. The normal 30-day revoked-profile retention remains in effect. A signed-out credential cannot start a new deletion request after restore; a previously recorded deletion confirmation remains independently retryable.

This protection applies to logout intents recorded by the recovery-aware version. Migration cannot reconstruct historical sign-outs that an older service never recorded in the independent journal. Account for those older restore points when defining the deployment backup-retirement policy.

The journal serializes both kinds of append commits through its head row, so an uncommitted lower sequence cannot be skipped by recovery. Primary replay applies one identity and advances its cursor in the same transaction. It holds the guest lock before sorted room locks, and releases them before moving to another identity. Multiple workers/retries can therefore share rooms without holding a previous player's room lock while acquiring the next player's guest lock. Already-applied deletion or revocation is idempotent and does not emit another room revision.

Replay rejects a different journal identity, a journal older than the saved cursor and missing sequence entries. Failed replay leaves readiness and authenticated operations unavailable until replay succeeds. Journal outages return an unavailable readiness response while liveness remains available. The worker replays up to ten identities before each normal tick; startup continues through bounded transactions until caught up. Runtime failures must be alerted by deployment monitoring.

The primary receipt expires after 30 days. Expiry does **not** remove suppression: a still-restorable old backup must not reactivate the identity. This version intentionally has no automatic journal pruning. Before public deployment, define the maximum backup/PITR/export lifetime and a reviewed retirement procedure that proves no remaining restore point needs a suppression entry. The journal is pseudonymous retained recovery data, not anonymous data or a claim of immediate erasure from every backup.

## Configuration

Supply credentials using process environment or a secret manager:

| Primary | Independent journal |
| --- | --- |
| `TAMBOLA_DATABASE_URL` | `TAMBOLA_DELETION_DATABASE_URL` |
| `TAMBOLA_DATABASE_USER` | `TAMBOLA_DELETION_DATABASE_USER` |
| `TAMBOLA_DATABASE_PASSWORD` | `TAMBOLA_DELETION_DATABASE_PASSWORD` |

Both URLs are PostgreSQL JDBC URLs. They must identify different database targets; startup also checks the actual visible tables to catch differently spelled URLs pointing to shared storage. Production uses the default `public` schema. These guards do not prove that two databases have independent provider backup policies. The journal must remain current when primary data is restored; a cluster-wide rollback containing both stores defeats that requirement. Protect the journal's availability and backups separately. [Separate migration/runtime roles](DATABASE_PERMISSIONS.md) are enforced at production startup, including preventing the journal runtime from deleting or altering existing intents. Provider permissions, durable writes, credential rotation and restricted-role restore acceptance remain deployment work.

For existing data, bind the primary database to the journal only during a controlled cutover. Earlier deletion receipts deliberately contain no player ID, so pre-journal deletions cannot be reconstructed from them. **Retire pre-journal primary restore points before accepting live profiles under this policy.** This repository has no hosted production users or approved hosted backup policy yet.

Without a journal, startup is allowed only with `TAMBOLA_LOCAL_DEVELOPMENT=true`, a loopback listener and an explicit `jdbc:postgresql://127.0.0.1:<port>/tambola_test` or `tambola_dev` URL. This exception is for isolated fixtures and does not provide deletion-after-restore protection. Native/process test drivers explicitly select this mode and clear inherited journal credentials so they cannot write to a production journal.

## Restore runbook

1. Isolate the replacement primary database from ingress and workers. Identify its trusted backup, source revision, schema versions and bound journal identity. Preserve the independently current journal; never restore it to the primary backup's timestamp.
2. Restore only primary data into the replacement database. Provision roles and secrets separately. PostgreSQL's [pg_dump](https://www.postgresql.org/docs/16/app-pgdump.html) produces a consistent logical archive; [pg_restore](https://www.postgresql.org/docs/16/app-pgrestore.html) restores selected archive objects. Logical schema drills do not exercise managed-provider PITR, role restoration or physical backups.
3. Quiesce primary writers during final cutover, including the old service and any deletion requests in flight. Point the new service at the replacement primary and independently current journal. Use this recovery-aware service revision or a later compatible one. Do not roll back to a pre-journal server binary.
4. Restore intended ownership/grants and run any needed migrations as a separate `--migrate` owner job, following [database permissions](DATABASE_PERMISSIONS.md). Start the service with restricted runtime credentials and without public ingress. It verifies both stores without DDL, checks permissions/separation/binding, and replays suppression before binding HTTP. On replay failure it must remain unavailable; repair the underlying cause and restart/retry. Never skip a cursor or manufacture a successful confirmation to force readiness.
5. Verify readiness, old deleted/signed-out access and device credentials rejected, surviving peers' ticket/call/score continuity, historical receipt redaction, matching deletion confirmation for a known fixture, and normal new-room play. Record exact app/service versions and backup/journal identifiers in restricted operational evidence.
6. Enable ingress only after those checks and provider-level restore validation pass. Retire the old primary according to the deployment retention policy. Continue backing up and monitoring the independent journal.

## Executed local drill and remaining acceptance

`BackupRestoreTest` uses PostgreSQL 16.9 `pg_dump`/`pg_restore` on a randomly named primary test schema in the explicit loopback `tambola_test` database. Its journal occupies a second schema excluded from that archive. It snapshots an active game with an earlier completed audit and peer retry receipt, deletes the host, records another deletion created after the backup, drops/restores only its owned primary schema, and confirms the restored snapshot really contains the old accessible identity before recovery. Replay must reject its credentials, redact all stored profile copies, retain peer tickets/calls/scores and draw commitment, recover both deletion confirmations, remain idempotent and let the peer continue calling.

The fixture recreates its owned namespace before schema-filtered restoration; `pg_restore --schema` selects objects within that namespace. An initial harness run omitted this step and failed before application recovery. That run is retained as failed evidence. Temporary dump/tool files and random schemas are cleaned up. No real user database is restored or dropped.

`node tools/server-deletion-recovery.mjs` separately runs actual Java processes with journal enforcement and explicit local fixture privileges against fresh primary/journal databases on the same isolated PostgreSQL instance. It confirms deletion, stops the server, restores the primary snapshot, proves the old guest exists again, rejects a wrong journal before any HTTP listener, then starts with the correct journal. The passing run verified redaction, rejected old access, identical confirmation, peer state/host continuation and cursor advancement. The script requires `CREATEDB` on the isolated test role and removes only its own databases, process and dump. Safe JSON evidence includes process IDs and the tested service JAR hash. See [original exact results](RECOVERY_VALIDATION.md) and the [permission-candidate rerun](PERMISSIONS_VALIDATION.md).

Run the complete service suite with the normal test database variables plus `TAMBOLA_PG_BIN` pointing to matching PostgreSQL 16 client binaries. Missing tools fail the drill; it is not silently skipped. Windows example: `C:\Program Files\PostgreSQL\16\bin`. The CI definition installs PostgreSQL 16 client tools on Ubuntu 24.04; editing CI is not evidence of a hosted CI run.

Local tests cover durable intent/primary rollback, journal-write failure, duplicates, concurrent writers/replayers, expired confirmation with retained suppression, journal identity/cursor/gap failures, readiness during outage and the actual logical restore. This establishes application-level recovery within the tested boundaries. It does **not** establish deployed backup independence, external-journal disaster recovery, provider PITR, large-history latency, production permissions, multi-region failover, cutover under live load or hosted operations.

## Copied installed Windows backup rehearsal

`tools/rehearse-windows-recovery.ps1` reads the installed upgrade's retained primary archive and snapshots its current independent journal with the restricted journal account. Sensitive copies stay in a new ACL-protected subdirectory of the installed host. It never rewinds the live journal, restores over the live databases or rotates the retained backup pairs.

The Node driver restores only fresh owned databases on the separate test PostgreSQL port **55432**. Four fresh roles separate migration ownership from runtime access. It uses the installed JDK and exact installed JAR distribution, runs an explicit migration job, reapplies grants and starts with `TAMBOLA_LOCAL_DEVELOPMENT=false`. Startup must finish journal replay and preserve surviving installed wallet/ledger rows. The installed HTTPS endpoint is checked before and after.

Further scenarios mutate only the isolated copies: create temporary wallets and device credentials, purchase/cancel tickets, snapshot primary, record logout/deletion, restore only that older primary and prove stale accessible records really returned before replay. Restricted startup must reject both bearer and device access, preserve the signed-out wallet, remove the deleted wallet, retain exact survivor purchase/refund responses and support a fresh purchase/refund followed by restart. Only hashes, counts, process IDs and assertion results leave the drill. Cleanup removes the owned databases/roles/process and protected archive copies; original backup hashes are checked independently.

Run from `full-game` in PowerShell 7 with Node available and the isolated `tambola_test` administrator credential already provisioned in `.test-workspace/postgres-password.txt`:

```powershell
./tools/rehearse-windows-recovery.ps1
```

The [27 September evidence](../reviews/installed-recovery-2026-09-27/README.md) passed with primary schema 006→007 and journal cursor 58→68. The installed snapshot contained one wallet and no logout intents; the explicit logout API scenario was tested in the isolated copy. This establishes copied installed-archive recovery with restricted roles, not a live-host cutover, provider backup independence or large-history recovery timing.
