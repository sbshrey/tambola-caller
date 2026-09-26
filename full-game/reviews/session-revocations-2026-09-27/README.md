# Logout protection across primary restore

Candidate on `shrey/full-tambola-game`, based on the device-session implementation at `36bd92ef2f97693f0b4aeb4681966ee90f9f7590`. The commit containing this evidence identifies the new source. The installed alpha16 Wi-Fi service and phone APK were not replaced in this checkpoint.

## Behavior

Journal migration 003 adds immutable logout intents containing only an opaque player ID, sequence and original revocation time. They share the deletion journal's existing commit order and primary replay cursor; no new primary schema or room protocol is needed beyond primary 006/protocol 4 from the session candidate. Access, enrollment, renewal and new deletion requests reject durably revoked credentials even before background replay.

Logout records commit before changing the primary profile. Replay expires access and clears its device proof without erasing the wallet or altering shared game history. Ordinary revoked-profile cleanup remains in effect. Primary rollback cannot undo the independent revocation. Journal failure cannot produce a successful logout. Existing deletion confirmations retain their original behavior. The English/Hindi privacy explanation and operational restore/permission instructions now cover both record types.

## Validation

The focused integration run passed **38 tests**, zero failures/errors/skips. `focused-summary.json` contains per-class counts; `focused-server.txt` records the actual Gradle run.

The complete build passed with **215 passing tests** represented in its reports: 125 server tests executed for this change; the unchanged domain 50, client 31 and Android app 9 remained Gradle UP-TO-DATE. No failures, errors or skipped tests are present. `full-summary.json` records per-class counts and this reuse boundary. Android debug assembly and lint passed. `final-distribution.txt` rebuilds the service after a documentation-comment edit; its JAR remained unchanged.

The real-process permission fixture then passed with separate non-superuser migration owners and restricted runtime logins. It migrated both databases, rejected owner/excessive permissions before listening, played/restarted/deleted a profile, enrolled a device, journaled logout over HTTP, rejected old access and renewal, denied destructive journal operations, and recovered from an injected worker permission failure. `restricted-process-evidence.json` records exact service/runtime hashes and successful removal of all owned processes, databases and roles. The matching metrics export contains no fixture identities or credentials.

- A real PostgreSQL primary `pg_dump`/`pg_restore` restores an active coin game from before access-token rotation/logout, retaining the independently current journal. Old access, newer access, device renewal, re-enrollment and new deletion requests remain rejected. Peer tickets/game state, wallet and ledger are unchanged. Replay is idempotent and retained revocation survives profile cleanup.
- Injected primary rollback leaves the committed revocation effective. Failed replay does not advance the cursor or falsely restore readiness. A later retry preserves the original revocation time.
- Injected journal write failure leaves the primary credential usable; journal outage remains unavailable. Mixed concurrent deletion/logout writers produce one contiguous committed sequence, and duplicate revocations retain their first timestamp.
- Missing entries and duplicate sequence kinds fail closed. A busy revoked profile defers mixed replay without blocking an unrelated player or skipping its cursor.
- A real journal version-2 upgrade preserves identity, deletion proof and primary cursor. The older registry verifier rejects version 3. Actual restricted database roles can append/replay revocations and cannot update, delete, truncate or alter those records.

## Boundaries

These restore tests use disposable schemas on the isolated PostgreSQL instance; they do not restore the installed host or establish its backup disaster recovery. Protection applies to sign-outs recorded by this version, not unrecorded historical sign-outs from an older service. Lost-device/reinstall wallet recovery remains separate work.

The installed host remained healthy over verified private-CA HTTPS; `installed-host-health.json` records the read-only probe. Windows Schannel could not determine private-CA revocation status, so the successful probe used Node's ordinary certificate-chain and endpoint verification. No certificate verification bypass was used.

The candidate still needs a guarded committed host upgrade and matching LAN acceptance. No new phone installer, physical-device check, native process-death proof, performance acceptance, Windows reboot or public release is established here. The main [coin plan](../../../docs/ONLINE_COIN_GAME_PLAN.md) retains those gates. Evidence contains no credentials, device/access tokens, raw database archives or private keys.
