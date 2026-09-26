# Online-profile deletion

This describes the implemented local service and Android behavior. Local deletion-after-restore validation passes. Alpha11 explains the current retention and recovery behavior in English/Hindi before registration, in Settings and at deletion. Public hosting, independent journal durability, a bounded provider backup/journal retirement policy and hosted/multi-process deletion acceptance remain release gates. Bounded retained-history memory and worker-contention checks now pass locally; see [the workload and limits](HISTORY_DELETION_VALIDATION.md). See [recovery validation](RECOVERY_VALIDATION.md).

## What a player can do

In Play online, choose **Delete online profile**, read the confirmation, then choose **Delete profile permanently**. This works from a lobby or an active/finished room. Cancelling the dialog does nothing. Offline games are independent.

The service removes the guest profile, its bearer access, enrolled device proof, coin wallet, creation receipts and personal command responses. It removes current memberships and replaces the profile's stored name with **Deleted player** and its avatar with the default in current rounds, archived results and other players' retry responses. It then clears that profile's local encrypted tickets, history and badges after the app receives confirmation. Device cleanup checks that the atomic file and its backup/new-file siblings are actually gone; a filesystem failure is not reported as successful cleanup.

If the deleted player hosted a room, control moves to a remaining player, preferring one who was recently connected, then earliest join time/ID. A room with no members closes and an unfinished round is cancelled. Other players can finish an active round with the originally agreed tickets, draw order, prizes and scores.

Shared game records retain opaque participant IDs, ticket numbers, calls, scores, rule/prize text and timestamps until normal room retention expires. Redaction targets the deleted identity's profile fields; it does not replace another person's matching name or search/alter arbitrary shared prize text. Copies already downloaded or shared by others are outside this request, and an existing backup is not instantly rewritten. This is profile deletion with stated shared-record retention, not a claim that every copy of all game content is erased.

Signing out only revokes access and clears this device's online data. **Reset online data** only clears local data. Neither substitutes for confirmed service deletion. Request deletion before signing out if that is the desired outcome. A new profile cannot reclaim an old profile's cards/history/badges.

## Protocol and uncertain outcomes

`POST /v1/guests/me/delete` accepts `DeleteProfileRequest { id }` and a bearer header. The ID is a canonical UUID generated and encrypted on the device **before** HTTP. The endpoint responds with `DeleteProfileReceipt { id, deletedAt, confirmUntil }`. The client verifies the response ID and timestamp ordering.

The app settles an interrupted access-token rotation before creating a deletion request. Once deletion is pending, it retains the same bearer token and stops background wallet/stream renewal until that request is confirmed. A lost deletion response must not cause a new token or request identity to replace its proof. Renewal itself consults the independent suppression journal; a restored device credential cannot reactivate a deleted profile.

The service first commits an intent to its independent deletion journal, then commits primary redaction and the confirmation in one primary PostgreSQL transaction. A confirmation key is SHA-256 of a versioned string containing the token and request ID. The primary receipt table contains only that hash and deletion/expiry times. The independent journal additionally retains an opaque player ID and sequence so an older backup can be redacted again; neither store keeps a raw token or display name in its deletion records. Presenting the same token plus request ID returns the original receipt for **30 days**, even though that token can no longer access any room. A different ID does not prove a previous deletion. There are 30 deletion attempts per minute per socket peer address; production ingress enforcement remains separate.

If the journal write fails, deletion cannot be confirmed. If it commits but primary redaction fails, the intent survives: that profile cannot authenticate, and replay or the original request completes primary redaction. A receipt is returned only after primary commit. Journal failure does not become deletion success, and expired confirmation does not remove the restore-suppression entry.

If connectivity fails, Android retains the encrypted original intent, blocks other mutations, suspends room streaming and offers **Retry pending action**. A recreated/restored session can retry the same intent. HTTP 401, local reset, a timeout or a missing room is never interpreted as deletion success. Resetting while deletion is pending explicitly warns that it discards the details needed to confirm that request. If the service confirmed deletion but local cleanup failed, the app distinguishes those outcomes and offers local cleanup recovery.

Newly expired but **unrevoked** credentials may request deletion only; they cannot read rooms or play. Revoked/signed-out tokens cannot initiate deletion. An already-issued matching confirmation remains retrievable until its expiry. There is no general account recovery or proof-of-identity support flow in this alpha.

## Stored records, locking and migration

Migration `002_profile_deletion.sql` adds a separate revocation timestamp, a historical participant index and deletion confirmations. Migration 003 adds a binding to the independent journal and a transactional replay cursor; journal migrations 001–002 create its records and guard head advancement. Earlier migrations stay unchanged. A separate owner `--migrate` job applies ordered migrations under advisory locks; ordinary startup verifies immutable checksums and [restricted runtime grants](DATABASE_PERMISSIONS.md), then replays deletion before opening HTTP. See the [restore runbook](BACKUP_RECOVERY.md) for required startup configuration and handling of pre-journal restore points.

The participant index covers current members/round players, past audits and command/create receipt actors. Backfill includes former members who no longer appear in the current lobby. Every later save records known guest participants. Deleted profiles are excluded from future index inserts even when their opaque player ID remains in an ongoing round.

Version-1 logout records cannot be distinguished from ordinary expired sessions. The migration conservatively treats tokens already expired at upgrade as revoked; it does not grant those old tokens new destructive authority. Fresh unrevoked expirations use the explicit new policy above.

Authentication holds a shared guest-row lock until its room transaction finishes and checks independent suppression. Deletion exclusively locks the guest, then affected rooms in ID order. This prevents a join/command authorized just before deletion from restoring the old name afterward. Worker room locks also use ID order within their fairness-selected batch. Primary room/profile edits, audit/receipt redaction and confirmation all roll back on failure; the independently committed intent remains for retry. Other players' command IDs and request hashes are preserved while their historical response profile fields are redacted. History is consumed through 32-row forward-only cursors within that same transaction; decoded histories are not collected into unbounded lists. Affected room commands may wait until commit. Background replay defers a busy cursor or guest row without advancing the cursor or clearing genuine recovery failures. Startup replay remains strict.

Rooms close 24 hours after creation; room records and audits expire 30 days later. Unenrolled or revoked guest rows are removed 30 days after session expiry; enabled device profiles and wallets survive that cleanup. Primary deletion confirmations are removed at `confirmUntil`. Short-lived rate records contain hashed socket addresses or opaque profile IDs. Independent journal entries intentionally have no automatic pruning: they must outlive every backup/PITR/export capable of restoring that identity. This is retained pseudonymous recovery data. Alpha11 explicitly discloses its contents and absent automatic expiry, separately from the 30-day confirmation window. The private Wi-Fi host retains seven daily primary/journal backup pairs; a public-provider backup policy and installed-host restore rehearsal remain open. Before public release, define and disclose the provider retention/retirement policy and validate backup isolation and restore cutover. Existing alpha10 and service release packages remain unchanged.

## Local fault fixture

Run the isolated test service on loopback **8081** and start `node tools/room-fault-proxy.mjs` from `full-game/`. The fixture binds only loopback: **8080** forwards HTTP/WebSockets to 8081; **8082** controls the test. It never logs request headers, tokens or bodies and is not part of the APK/server distribution.

After installing both debug APKs on the dedicated test emulator:

```powershell
adb -s emulator-5582 reverse tcp:8080 tcp:8080
adb -s emulator-5582 reverse tcp:8082 tcp:8082
node tools/android-smoke.mjs --online --fault-proxy --label deletion-tests
```

The opt-in deletion journey arms the fixture to drop every successful deletion response after the upstream transaction commits, including transparent transport retries. It asserts that the app keeps its original encrypted request, recreates the activity, allows responses again, retries, checks local cleanup and continuing peer host control, and verifies the existing offline round's ID/cards/calls/marks. The fixture's counters contain no player/request identifiers. Stop the owned fixture/server processes and remove these two emulator mappings after testing.

This original journey uses real loopback HTTP response loss with activity recreation. A separate [cold-process acceptance driver](../RECOVERY_AND_MULTIPLAYER_VALIDATION.md) now verifies that the actual Android process ends, a new process restores the same encrypted request, and retry confirms deletion while retaining offline progress. Mobile network switching, physical-phone UIs, TLS, hosted HTTP/max-payload deletion latency and independent provider backup restoration remain separate acceptance work. The [retained-history fixture](HISTORY_DELETION_VALIDATION.md) records the local bounded-heap and background-worker evidence.
