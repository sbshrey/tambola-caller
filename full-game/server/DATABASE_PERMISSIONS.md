# Database owners and runtime credentials

Production-mode startup verifies migrations and effective privileges before opening HTTP. It performs no automatic schema migration. Use four independent, dedicated roles: a primary migration owner, a journal migration owner, a primary runtime login and a journal runtime login. Do not give runtime roles memberships, ownership, administrative attributes or grant options. Local verification is recorded in [permissions validation](PERMISSIONS_VALIDATION.md); a provider deployment still needs its own acceptance.

## Provision and upgrade

1. Provision two dedicated databases and four distinct roles through the provider's administrative channel. Each owner owns only its intended database and objects. Runtime roles have `LOGIN`, `NOSUPERUSER`, `NOCREATEDB`, `NOCREATEROLE`, `NOREPLICATION`, `NOBYPASSRLS`, no memberships and no owned objects. Use separate randomly generated credentials kept in the secret manager. The grant templates do not create roles or passwords.
2. Stop writers for a controlled upgrade and retain verified primary backup/current independent journal recovery evidence. Use the exact candidate distribution with JDK 17. Supply the six database environment variables described in [the restore runbook](BACKUP_RECOVERY.md), with **owner** credentials, to a one-off job. Leave `TAMBOLA_LOCAL_DEVELOPMENT` unset/false. Run `server/bin/server --migrate` on Linux, or `server\bin\server.bat --migrate` on Windows, from the extracted distribution. It migrates journal then primary, checks the storage boundary, closes connections and exits without an HTTP listener. An error must stop the deployment.
3. Connect as each database's owner and apply its matching grant file using psql. For example, `psql --set=ON_ERROR_STOP=1 --set=database=tambola_rooms --set=runtime_role=tambola_rooms_app --file=primary.sql`, then the equivalent `journal.sql` command for the journal database and runtime role. Supply host, database, owner and password through protected environment/secret-manager integration, never password arguments. Templates are in `src/main/resources/db/permissions/` and copied beside the service release evidence. Review the names before execution: these templates revoke PUBLIC database access and table grants and are for **dedicated databases only**.
4. Start the service with the two **runtime** credentials, using no command-line arguments. Owner credentials must not be present in this process. Keep local-development mode disabled. Verify readiness, a fictional two-player round, profile deletion/retry, restart and provider-specific recovery before exposing ingress. TLS and network controls remain required.
5. Rotate credentials through the secret manager and restart/revalidate the service. Startup checks do not continuously detect later ACL drift. Audit permissions administratively after every change. Do not solve a failed check by enabling local-development mode or granting ownership.

Grant templates establish the table privileges and intended column updates on fresh, independently provisioned roles. Existing column-level grants or memberships must be explicitly reviewed/revoked or the roles replaced; the startup audit rejects excess effective privileges, including inherited/PUBLIC grants and column grant options. PostgreSQL distinguishes object-owner powers from ordinary grants, and default PUBLIC privileges include database CONNECT/TEMP. See [privilege behavior](https://www.postgresql.org/docs/16/ddl-priv.html) and [GRANT](https://www.postgresql.org/docs/16/sql-grant.html).

## Effective runtime access

Both runtime roles may CONNECT to their own database, use `public`, and SELECT their expected application tables. Neither has database CREATE/TEMP, schema CREATE, object ownership, grant options, table TRUNCATE/TRIGGER/REFERENCES or administrative/role-membership authority. Migration registries are read-only.

| Store | Writes allowed |
| --- | --- |
| Primary | INSERT/UPDATE/DELETE on guests and rooms; INSERT on create receipts; INSERT/DELETE plus response-only UPDATE on command receipts; INSERT/DELETE on events and room participants; INSERT/DELETE plus requests-only UPDATE on rate limits; INSERT plus payload-only UPDATE on finished rounds; INSERT plus response-only UPDATE on match receipts; INSERT/DELETE on deletion confirmations; journal ID/applied-sequence-only UPDATE on the recovery cursor; INSERT on coin wallets/ledger and refill-after-only UPDATE on wallets |
| Journal | INSERT new deletion intents and session revocations; UPDATE only the identity row's head column |

Journal migration **002** adds a security-invoker trigger; migration **003** extends it to deletion and logout intents. Head can advance only by one, with exactly one corresponding intent present in the same transaction; identity cannot change. Existing journal rows cannot be updated or deleted by its runtime account. Owners retain administrative restoration authority. The current identity candidate uses primary schema **006**, journal schema **003** and room protocol **4**. Reapply the journal grant template after migration to grant SELECT/INSERT on `session_revocations`.

The startup audit checks actual PostgreSQL effective privileges rather than trusting role names. It rejects missing/excessive table/column grants, owners, memberships and administrative attributes. Its scope is the application's database objects and startup configuration; it is not a substitute for auditing provider administrators, extensions, functions, network access, backups or subsequent privilege changes. Cross-database runtime connections are denied in the isolated proof by revoking PUBLIC CONNECT and granting it only to the matching login. Enforce that boundary at the provider too.

## Restore and rollback

Follow [BACKUP_RECOVERY.md](BACKUP_RECOVERY.md): preserve the current independent journal while restoring primary data. A restore with `--no-owner --no-privileges` needs the intended ownership and these grants re-established before runtime startup. Run any needed migrations separately as the owners, then return to runtime credentials. An ordinary startup never repairs a missing migration, bad checksum or future version.

Do not downgrade past journal migration 003 using an older binary as a routine rollback. Current binaries reject unknown installed migrations; older binaries may lack these checks or try to migrate with runtime credentials. A rollback needs a separately reviewed compatible candidate. Never remove suppression entries or rewind the journal to make an older service start. The existing local logical-restore drill uses privileged fixture roles; restricted-role provider restore/cutover remains an acceptance gate.

## Isolated test exception

`TAMBOLA_LOCAL_DEVELOPMENT=true` explicitly permits automatic migration and privileged fixture credentials only with a loopback listener and exact `127.0.0.1` JDBC URLs. Allowed database names are `tambola_test`, `tambola_dev`, or generated `tambola_load_*` / `tambola_recovery_*` names matching the code's 16-hex run ID patterns. Both database targets are checked. URL query strings, remote journal targets and arbitrary database names are rejected. Without a journal, only the test/dev primary exception is allowed and provides no deletion-after-restore guarantee. With a journal, replay and storage-boundary checks remain active.

JUnit integration tests and the process drivers require an isolated `tambola_test` administrator able to create schemas, databases and roles; never use production credentials. The permission proof creates four non-superuser login roles and two databases, runs the production startup path with local-development mode false, then removes its owned resources. Run from `full-game/` after `:server:installDist`:

```text
node tools/server-permissions-smoke.mjs
```

Provide `JAVA_HOME`, the three `TAMBOLA_TEST_DATABASE_*` variables and `TAMBOLA_PG_BIN` for PostgreSQL 16 clients. Safe evidence records stages and the exact service JAR hash; random role passwords remain in memory/environment/SQL stdin and are not exported.
