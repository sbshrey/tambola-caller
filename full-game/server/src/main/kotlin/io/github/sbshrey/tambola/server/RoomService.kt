package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import java.sql.Connection
import java.sql.SQLException
import java.util.UUID

internal const val OPEN_COIN_LOBBY_SQL = """SELECT payload FROM rooms WHERE matchable AND phase = 'LOBBY' AND expires_at > ?
    AND coin_starts_at > ? AND coin_human_seats < 8
    AND coalesce((payload::jsonb->'options'->>'coinRulesVersion')::integer, 1) = 1
    ORDER BY coin_starts_at, id LIMIT 1 FOR UPDATE"""

internal const val OPEN_EXPANDED_LOBBY_SQL = """SELECT payload FROM rooms WHERE matchable AND phase = 'LOBBY' AND expires_at > ?
    AND coin_starts_at > ? AND coin_human_seats < 50
    AND (payload::jsonb->'options'->>'coinRulesVersion')::integer = 2
    AND coalesce((payload::jsonb->'options'->>'powersEnabled')::boolean, false) = false
    ORDER BY coin_starts_at, id LIMIT 1 FOR UPDATE"""

/** Room changes commit atomically; deletion and logout first record intent in the independent journal. */
class RoomService(
    private val database: Database,
    private val clock: () -> Long = System::currentTimeMillis,
    private val journal: DeletionJournal? = null,
    private val ads: RewardedAds? = null,
) {
    fun prepareAd(token: String): RewardAdIntent {
        val rewards = ads ?: fail(503, "ads_disabled", "Rewarded ads are not available yet.")
        authenticatedRate(token, "ad_prepare", 15)
        return database.transaction { connection ->
            val guest = authenticate(connection, token, lock = true)
            rewards.prepare(connection, guest.id, clock())
        }
    }
    fun adStatus(token: String, id: String): RewardAdStatus {
        val rewards = ads ?: fail(503, "ads_disabled", "Rewarded ads are not available yet.")
        authenticatedRate(token, "ad_status", 60)
        return database.transaction { connection -> rewards.status(connection, authenticate(connection, token).id, id) }
    }
    fun verifyAd(query: String) {
        val rewards = ads ?: fail(503, "ads_disabled", "Rewarded ads are not available yet.")
        rate("ad_callback", 300)
        val verified = rewards.verified(query, clock())
        recoveryHealthy()
        database.transaction { rewards.credit(it, verified, clock()) }
    }
    fun loginRewards(token: String): LoginRewards {
        authenticatedRate(token, "login_rewards", 30)
        return database.transaction { connection ->
            val guest = authenticate(connection, token, lock = true)
            collectLoginRewards(connection, guest.id, clock())
        }
    }
    @Volatile private var replayFailure = false
    fun register(request: GuestRequest, source: String): GuestCredentials {
        val name = request.displayName.trim()
        demand(name.length in 1..40 && name.none(Char::isISOControl) && request.avatar in 0 until AVATAR_COUNT,
            400, "invalid_guest", "Use a name of 1–40 characters and a supported avatar.")
        recoveryHealthy()
        rate("guest:${digest(source)}", 60)
        val token = secret()
        val credentials = GuestCredentials(UUID.randomUUID().toString(), token, clock() + SESSION_LIFETIME)
        database.transaction {
            it.execute("INSERT INTO guests (id, name, avatar, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)", credentials.playerId, name, request.avatar, digest(token), credentials.expiresAt)
            CoinLedger.open(it, credentials.playerId, clock())
        }
        return credentials
    }

    fun wallet(token: String): WalletView {
        authenticatedRate(token, "wallet", 120)
        return database.transaction { connection ->
            val guest = authenticate(connection, token)
            CoinLedger.open(connection, guest.id, clock())
        }
    }

    /** Enrollment is repeatable only with the same proof; a game session cannot replace it. */
    fun enrollDevice(token: String, request: EnrollDeviceRequest): DeviceEnrollment {
        validToken(request.deviceKey)
        demand(request.deviceKey != token, 400, "invalid_device_key", "Use a separate device credential.")
        authenticatedRate(token, "enroll", 10)
        return deviceTransaction { connection ->
            val guest = authenticate(connection, token, lock = true)
            val existing = connection.query("SELECT device_key_hash, session_revision FROM guests WHERE id = ?", guest.id) {
                it.getString(1) to it.getLong(2)
            }.single()
            val hash = digest(request.deviceKey)
            demand(existing.first == null || existing.first == hash, 409, "device_enrolled", "This profile already has a device credential.")
            if (existing.first == null) connection.execute("UPDATE guests SET device_key_hash = ? WHERE id = ?", hash, guest.id)
            DeviceEnrollment(guest.id, existing.second)
        }
    }

    /** Rotating access credentials never touch wallet entries, purchases or operation receipts. */
    fun renewSession(deviceKey: String, request: RenewSessionRequest, source: String): RenewedSession {
        validToken(deviceKey); validToken(request.token)
        demand(request.expectedRevision in 0 until Long.MAX_VALUE && deviceKey != request.token,
            400, "invalid_session", "Use a new session token and a valid revision.")
        recoveryHealthy()
        rate("renew-source:${digest(source)}", 300)
        rate("renew-device:${digest(deviceKey)}", 10)
        return deviceTransaction { connection ->
            verifyJournal(connection)
            val entry = connection.query("SELECT id, token_hash, expires_at, session_revision FROM guests WHERE device_key_hash = ? AND revoked_at IS NULL FOR UPDATE", digest(deviceKey)) {
                SessionEntry(it.getString(1), it.getString(2), it.getLong(3), it.getLong(4))
            }.singleOrNull() ?: fail(401, "unauthorized", "This device credential is unavailable or was revoked.")
            demand(journal?.blocksAccess(entry.id) != true, 401, "unauthorized", "This profile's credentials were revoked.")
            val hash = digest(request.token)
            if (entry.revision == request.expectedRevision + 1 && entry.hash == hash)
                return@deviceTransaction RenewedSession(GuestCredentials(entry.id, request.token, entry.expiresAt), entry.revision)
            demand(entry.revision == request.expectedRevision, 409, "session_changed", "A newer session has already been issued.")
            demand(entry.hash != hash, 400, "invalid_session", "Use a new session token.")
            val expiry = clock() + SESSION_LIFETIME
            connection.execute("UPDATE guests SET token_hash = ?, expires_at = ?, session_revision = session_revision + 1 WHERE id = ?", hash, expiry, entry.id)
            RenewedSession(GuestCredentials(entry.id, request.token, expiry), entry.revision + 1)
        }
    }

    private data class SessionEntry(val id: String, val hash: String, val expiresAt: Long, val revision: Long)

    private fun <T> deviceTransaction(block: (Connection) -> T): T = try { database.transaction(block = block) }
    catch (error: SQLException) {
        if (error.sqlState == "23505") fail(409, "credential_conflict", "Use a different random credential.")
        throw error
    }

    fun refill(token: String, request: RefillRequest): WalletView {
        validId(request.id)
        authenticatedRate(token, "refill", 10)
        return database.transaction { connection ->
            val guest = authenticate(connection, token)
            CoinLedger.open(connection, guest.id, clock())
            CoinLedger.refill(connection, guest.id, request.id, clock())
        }
    }

    /** Joining, buying tickets and the durable receipt are one transaction. */
    fun match(token: String, request: MatchRequest): RoomUpdate {
        validId(request.id)
        request.previousFriendRound?.let(::validId)
        authenticatedRate(token, "match", 20)
        return database.transaction { connection ->
            val guest = authenticate(connection, token, lock = true)
            val hash = digest(WireJson.encodeToString(request))
            val receipt = connection.query("SELECT request_hash, response FROM match_receipts WHERE actor = ? AND command_id = ?", guest.id, request.id) {
                Receipt(it.getString(1), it.getString(2))
            }.singleOrNull()
            if (receipt != null) {
                demand(receipt.hash == hash, 409, "id_reused", "This purchase ID already has different ticket choices.")
                return@transaction WireJson.decodeFromString<RoomUpdate>(receipt.response)
            }
            val now = clock()
            val existing = connection.query("""SELECT payload FROM rooms WHERE (payload::jsonb->'options'->>'coinGame')::boolean AND phase IN ('LOBBY','ACTIVE')
                AND expires_at > ? AND id IN (SELECT room_id FROM room_participants WHERE player_id = ?)
                AND EXISTS (SELECT 1 FROM jsonb_array_elements(payload::jsonb->'members') m WHERE m->>'id' = ?)
                ORDER BY id LIMIT 1 FOR UPDATE""", now, guest.id, guest.id) { decode(it.getString(1)) }.singleOrNull()
            val saved = if (existing != null) {
                demand(request.powersEnabled || !existing.options.powersEnabled, 409, "update_required", "Rejoin this Power room with the updated app.")
                demand(request.rulesVersion >= existing.options.coinRulesVersion, 409, "update_required", "Update the app to rejoin this table.")
                // Re-entering an owned game never buys a second entry.
                touch(connection, existing, guest.id, now)
            } else {
                // The guest lock already serializes this player's entry. Prepare their
                // wallet before serializing shared lobby selection; all writes still
                // roll back together if the purchase cannot complete.
                CoinLedger.ensure(connection, guest.id, now)
                if (!request.friendTable) PurchaseTiming.allocation {
                    connection.query("SELECT pg_advisory_xact_lock(749023809)") { true }
                }
                val purchaseAt = clock()
                val previous = request.previousFriendRound?.let { roundId ->
                    load(connection, requireNotNull(request.friendCode)).also {
                        member(it, guest.id)
                        demand(it.friendTable && it.phase == RoomPhase.FINISHED && it.round?.id == roundId,
                            409, "friend_round_changed", "Choose a completed friends round to play together again.")
                    }
                }
                val joinCode = if (previous != null) previous.nextFriendCode else request.friendCode
                val waiting = when {
                    joinCode != null -> load(connection, joinCode).also {
                        demand(it.options.powersEnabled == request.powersEnabled, 409, "power_room_mismatch", "Choose the same Classic or Power mode as your friends.")
                        demand(it.options.coinRulesVersion == request.rulesVersion, 409, "update_required", "Everyone at a friends table needs the same game rules. Update the app and create a new table.")
                        demand(it.friendTable && it.phase == RoomPhase.LOBBY && !it.locked, 409, "friend_table_closed", "That friend table is not accepting players.")
                        demand(it.members.size < it.options.capacity, 409, "room_full", "This table is full.")
                    }
                    request.friendTable -> null
                    else -> connection.query(if (request.rulesVersion == 2) OPEN_EXPANDED_LOBBY_SQL.let {
                        if (request.powersEnabled) it.replace("= false", "= true") else it
                    } else OPEN_COIN_LOBBY_SQL, purchaseAt, purchaseAt) { decode(it.getString(1)) }.singleOrNull()
                }
                val room = waiting ?: RoomRecord(UUID.randomUUID().toString(), roomCode(), guest.id,
                    coinOptions(rulesVersion = request.rulesVersion).copy(powersEnabled = request.powersEnabled).let {
                        if (request.friendTable || request.powersEnabled) it.copy(computerPlayers = 0) else it
                    }, emptyList(),
                    purchaseAt + if (request.friendTable) FRIEND_LOBBY_LIFETIME else ROOM_LIFETIME,
                    startsAt = (purchaseAt + MATCH_COUNTDOWN).takeUnless { request.friendTable }, friendTable = request.friendTable).also {
                    connection.execute("INSERT INTO rooms (id, code, phase, expires_at, payload, matchable) VALUES (?, ?, ?, ?, ?, ?)",
                        it.id, it.code, it.phase.name, it.expiresAt, WireJson.encodeToString(it), !request.friendTable)
                }
                // The previous-room lock serializes simultaneous replay clicks. Linking and
                // the first debit commit together, so a failed purchase cannot strand the group.
                if (previous != null && previous.nextFriendCode == null) save(connection, previous.copy(nextFriendCode = room.code))
                demand(room.expiresAt > clock() && (room.friendTable || requireNotNull(room.startsAt) > clock()), 409, "sales_closed", "That round is starting. Try Play again.")
                changed(connection, room.copy(
                    members = room.members + Member(guest.id, guest.name, guest.avatar, purchaseAt, ready = true, lastSeen = purchaseAt),
                    powerUps = (room.powerUps - guest.id) + if (request.powerUp == PowerUp.NONE) emptyMap() else mapOf(guest.id to request.powerUp),
                    purchases = room.purchases + (guest.id to request.tickets)), "tickets_bought", purchaseAt, before = room)
            }
            val response = update(connection, saved, guest.id, null)
            connection.execute("INSERT INTO match_receipts VALUES (?, ?, ?, ?, ?)", guest.id, request.id, hash, saved.id, WireJson.encodeToString(response))
            response
        }
    }

    fun revoke(token: String) = database.transaction { connection ->
        val guest = authenticate(connection, token, lock = true)
        val intent = journal?.revoke(guest.id, clock()) ?: RevocationIntent(0, guest.id, clock())
        applyRevocation(connection, intent)
    }

    /** Durable suppression precedes the atomic primary-database mutation and its confirmation. */
    fun deleteProfile(token: String, request: DeleteProfileRequest, source: String): DeleteProfileReceipt {
        validId(request.id)
        validToken(token)
        rate("delete:${digest(source)}", 30)
        val proof = digest("tambola-delete-v1\n$token\n${request.id}")
        return database.transaction { connection ->
            verifyJournal(connection)
            fun receipt() = connection.query("SELECT deleted_at, expires_at FROM deletion_receipts WHERE confirmation_hash = ? AND expires_at > ?", proof, clock()) {
                DeleteProfileReceipt(request.id, it.getLong(1), it.getLong(2))
            }.singleOrNull()
            receipt()?.let { return@transaction it }
            val recorded = journal?.find(proof)
            val intent = if (recorded != null) {
                demand(recorded.confirmUntil > clock(), 401, "unauthorized", "This deletion confirmation has expired.")
                recorded
            } else {
                // Expired but unrevoked credentials may initiate deletion, never read/play again.
                val playerId = connection.query("SELECT id FROM guests WHERE token_hash = ? AND revoked_at IS NULL FOR UPDATE", digest(token)) { it.getString(1) }
                    .singleOrNull() ?: return@transaction (receipt() ?: fail(401, "unauthorized", "Deletion could not be confirmed with this session. Local reset does not delete server data."))
                demand(journal?.blocksAccess(playerId) != true, 401, "unauthorized", "This profile's credentials were revoked.")
                val now = clock()
                journal?.append(playerId, proof, now, now + 30 * ROOM_LIFETIME)
                    ?: DeletionIntent(0, playerId, proof, now, now + 30 * ROOM_LIFETIME)
            }
            applyDeletion(connection, intent)
            DeleteProfileReceipt(request.id, intent.deletedAt, intent.confirmUntil)
        }
    }

    /** Replays deletion and logout intents. One identity per transaction preserves guest-before-room ordering. */
    fun replayDeletions(limit: Int = 100, skipBusy: Boolean = false): Int {
        try {
            val result = replayDeletionBatch(limit, skipBusy)
            if (result.caughtUp) replayFailure = false
            return result.applied
        } catch (error: Exception) { replayFailure = true; throw error }
    }

    private data class ReplayResult(val applied: Int, val caughtUp: Boolean)
    private enum class ReplayStep { APPLIED, CAUGHT_UP, BUSY }

    private fun replayDeletionBatch(limit: Int, skipBusy: Boolean): ReplayResult {
        val journal = journal ?: return ReplayResult(0, true)
        require(limit in 1..1000)
        var applied = 0
        repeat(limit) {
            val step = database.transaction { connection ->
                val saved = connection.query("SELECT journal_id, applied_sequence FROM deletion_recovery WHERE singleton FOR UPDATE${if (skipBusy) " SKIP LOCKED" else ""}") {
                    it.getString(1) to it.getLong(2)
                }.singleOrNull()
                if (saved == null) {
                    recoveryCheck(skipBusy && connection.query("SELECT 1 FROM deletion_recovery WHERE singleton") { true }.isNotEmpty(),
                        "Deletion recovery cursor is missing")
                    return@transaction ReplayStep.BUSY
                }
                val head = journal.position()
                recoveryCheck(saved.first == null || saved.first == head.id, "Primary database belongs to another deletion journal")
                recoveryCheck(saved.second <= head.head, "Deletion journal is older than the primary recovery cursor")
                val next = journal.next(saved.second, head.head)
                if (next != null && skipBusy) {
                    val locked = connection.query("SELECT id FROM guests WHERE id = ? FOR UPDATE SKIP LOCKED", next.playerId) { it.getString(1) }.isNotEmpty()
                    // A live deletion or previously authorized command owns this guest. Do not
                    // advance the cursor or treat ordinary contention as a recovery failure.
                    // An absent guest still needs replay: retained records may come from a restore.
                    if (!locked && connection.query("SELECT 1 FROM guests WHERE id = ?", next.playerId) { true }.isNotEmpty())
                        return@transaction ReplayStep.BUSY
                }
                when (next) {
                    is DeletionIntent -> applyDeletion(connection, next)
                    is RevocationIntent -> applyRevocation(connection, next)
                    null -> Unit
                }
                connection.execute("UPDATE deletion_recovery SET journal_id = ?, applied_sequence = ? WHERE singleton", head.id, next?.sequence ?: saved.second)
                if (next != null) ReplayStep.APPLIED else ReplayStep.CAUGHT_UP
            }
            if (step != ReplayStep.APPLIED) return ReplayResult(applied, step == ReplayStep.CAUGHT_UP)
            applied++
        }
        return ReplayResult(applied, false)
    }

    fun recoveryHealthy(): Boolean = database.transaction { verifyJournal(it); true }

    private fun verifyJournal(connection: Connection) {
        val journal = journal ?: return
        verifyJournalPosition(recoveryPosition(connection), journal.position())
    }

    private fun recoveryPosition(connection: Connection): JournalPosition? {
        recoveryCheck(!replayFailure, "Deletion recovery requires a successful retry")
        return connection.query("SELECT journal_id, applied_sequence FROM deletion_recovery WHERE singleton") { row ->
            row.getString(1)?.let { JournalPosition(it, row.getLong(2)) }
        }.single()
    }

    private fun verifyJournalPosition(saved: JournalPosition?, head: JournalPosition) {
        recoveryCheck(saved != null && saved.id == head.id && saved.head <= head.head, "Deletion recovery is not initialized or its journal is inconsistent")
    }

    private fun applyDeletion(connection: Connection, intent: DeletionIntent) {
        val playerId = intent.playerId
        val now = clock()
        connection.query("SELECT id FROM guests WHERE id = ? FOR UPDATE", playerId) { it.getString(1) }
        connection.forEachRow("SELECT payload FROM rooms WHERE id IN (SELECT room_id FROM room_participants WHERE player_id = ?) ORDER BY id FOR UPDATE", playerId) { row ->
            val original = decode(row.getString(1))
            val redacted = original.redact(playerId)
            val remaining = redacted.members.filterNot { it.id == playerId }
            val successor = remaining.minWithOrNull(compareBy<Member> { now - it.lastSeen >= PRESENCE_TIMEOUT }.thenBy { it.joinedAt }.thenBy { it.id })
            val next = redacted.copy(members = remaining,
                purchases = if (redacted.phase == RoomPhase.LOBBY) redacted.purchases - playerId else redacted.purchases,
                powerUps = if (redacted.phase == RoomPhase.LOBBY) redacted.powerUps - playerId else redacted.powerUps,
                hostId = if (original.hostId == playerId) successor?.id.orEmpty() else original.hostId,
                phase = if (remaining.isEmpty()) RoomPhase.CLOSED else original.phase,
                round = if (remaining.isEmpty()) redacted.round?.cancel() else redacted.round,
                nextDrawAt = if (remaining.isEmpty()) null else redacted.nextDrawAt,
                computerClaimsAt = if (remaining.isEmpty()) emptyMap() else redacted.computerClaimsAt)
            changed(connection, next, "profile_deleted", now)
            connection.forEachRow("SELECT round_id, payload FROM finished_rounds WHERE room_id = ? ORDER BY round_id FOR UPDATE", original.id) { row ->
                val id = row.getString(1)
                val archive = decode(row.getString(2))
                connection.execute("UPDATE finished_rounds SET payload = ? WHERE room_id = ? AND round_id = ?", WireJson.encodeToString(archive.redact(playerId)), original.id, id)
            }
            connection.forEachRow("SELECT actor, command_id, response FROM command_receipts WHERE room_id = ? AND actor <> ? ORDER BY actor, command_id FOR UPDATE", original.id, playerId) { row ->
                val actor = row.getString(1)
                val id = row.getString(2)
                val response = WireJson.decodeFromString<RoomUpdate>(row.getString(3))
                connection.execute("UPDATE command_receipts SET response = ? WHERE room_id = ? AND actor = ? AND command_id = ?", WireJson.encodeToString(response.redact(playerId)), original.id, actor, id)
            }
        }
        connection.forEachRow("SELECT actor, command_id, response FROM match_receipts WHERE room_id IN (SELECT room_id FROM room_participants WHERE player_id = ?) AND actor <> ? ORDER BY actor, command_id FOR UPDATE", playerId, playerId) { row ->
            val response = WireJson.decodeFromString<RoomUpdate>(row.getString(3))
            connection.execute("UPDATE match_receipts SET response = ? WHERE actor = ? AND command_id = ?",
                WireJson.encodeToString(response.redact(playerId)), row.getString(1), row.getString(2))
        }
        connection.execute("DELETE FROM command_receipts WHERE actor = ?", playerId)
        connection.execute("DELETE FROM room_participants WHERE player_id = ?", playerId)
        listOf("create", "join", "read", "command", "wallet", "refill", "match").forEach { connection.execute("DELETE FROM rate_limits WHERE bucket = ?", "$it:$playerId") }
        connection.execute("DELETE FROM guests WHERE id = ?", playerId)
        if (intent.confirmUntil > now) connection.execute("INSERT INTO deletion_receipts VALUES (?, ?, ?) ON CONFLICT (confirmation_hash) DO NOTHING", intent.proof, intent.deletedAt, intent.confirmUntil)
    }

    fun create(token: String, request: CreateRoomRequest): RoomUpdate {
        validId(request.id)
        demand(!request.options.coinGame, 400, "use_matchmaking", "Use Play to enter a coin round.")
        authenticatedRate(token, "create", 10)
        return database.transaction { connection ->
            val guest = authenticate(connection, token, lock = true)
            val hash = digest(WireJson.encodeToString(request))
            val previous = connection.query("SELECT request_hash, room_id FROM create_receipts WHERE actor = ? AND command_id = ?", guest.id, request.id) { it.getString(1) to it.getString(2) }.singleOrNull()
            if (previous != null) {
                demand(previous.first == hash, 409, "id_reused", "This request ID was already used for different settings.")
                val room = connection.query("SELECT payload FROM rooms WHERE id = ? FOR UPDATE", previous.second) { decode(it.getString(1)) }.single()
                member(room, guest.id)
                return@transaction update(connection, room, guest.id, null)
            }
            val active = connection.query("SELECT count(*) FROM create_receipts c JOIN rooms r ON r.id = c.room_id WHERE c.actor = ? AND r.expires_at > ? AND r.phase <> 'CLOSED'", guest.id, clock()) { it.getInt(1) }.single()
            demand(active < 5, 429, "room_limit", "This profile already has five unexpired rooms.")
            val now = clock()
            val room = RoomRecord(UUID.randomUUID().toString(), roomCode(), guest.id, request.options,
                listOf(Member(guest.id, guest.name, guest.avatar, now, lastSeen = now)), now + ROOM_LIFETIME)
            connection.execute("INSERT INTO rooms (id, code, phase, expires_at, payload) VALUES (?, ?, ?, ?, ?)", room.id, room.code, room.phase.name, room.expiresAt, WireJson.encodeToString(room))
            val saved = changed(connection, room, "created", now)
            connection.execute("INSERT INTO create_receipts VALUES (?, ?, ?, ?)", guest.id, request.id, hash, room.id)
            update(connection, saved, guest.id, null)
        }
    }

    fun join(token: String, code: String): RoomUpdate {
        authenticatedRate(token, "join", 20)
        return database.transaction { connection ->
            val guest = authenticate(connection, token)
            val room = load(connection, code)
            val now = clock()
            val joined = if (room.members.any { it.id == guest.id }) touch(connection, room, guest.id, now) else {
                demand(!room.options.coinGame, 409, "use_matchmaking", "Use Play to buy tickets for an accepting round.")
                demand(room.phase == RoomPhase.LOBBY && !room.locked, 409, "room_locked", "This room is not accepting new players.")
                demand(room.members.size + room.options.computerPlayers < room.options.capacity, 409, "room_full", "This room is full.")
                changed(connection, room.copy(members = room.members + Member(guest.id, guest.name, guest.avatar, now, lastSeen = now)), "joined", now)
            }
            update(connection, joined, guest.id, null)
        }
    }

    fun read(token: String, code: String, after: Long? = null): RoomUpdate {
        authenticatedRate(token, "read", 300)
        return database.transaction { connection ->
        val guest = authenticate(connection, token)
        val room = load(connection, code)
        member(room, guest.id)
        update(connection, touch(connection, room, guest.id, clock()), guest.id, after)
        }
    }

    private fun applyRevocation(connection: Connection, intent: RevocationIntent) {
        connection.execute("UPDATE guests SET expires_at = ?, revoked_at = ?, device_key_hash = NULL WHERE id = ?",
            intent.revokedAt, intent.revokedAt, intent.playerId)
    }

    internal fun admitEvents(token: String) = authenticatedRate(token, "events", 20)

    /** Quiet server-driven polls must not generate rate-limit or row-lock writes. */
    internal fun pollEvents(token: String, code: String, after: Long? = null): RoomUpdate =
        checkNotNull(pollEvents(token, code, after, onlyIfChanged = false))

    /** Suppression is allowed only after a stream has sent its initial snapshot. */
    internal fun pollEvents(token: String, code: String, after: Long?, onlyIfChanged: Boolean): RoomUpdate? {
        fun response(connection: Connection, room: RoomRecord, actor: String): RoomUpdate? =
            if (onlyIfChanged && after == room.revision) null else update(connection, room, actor, after)

        var renewPresence = false
        val quiet = database.transaction(readOnly = true) { connection ->
            val guest = authenticate(connection, token, protect = false)
            val room = load(connection, code, lock = false)
            member(room, guest.id)
            renewPresence = needsTouch(room, guest.id, clock())
            if (renewPresence) null else response(connection, room, guest.id)
        }
        if (!renewPresence) return quiet
        // Revalidate under the normal guest-before-room locks before renewing presence.
        // Never upgrade room locks ahead of a profile deletion's guest lock.
        return database.transaction { connection ->
            val guest = authenticate(connection, token)
            val room = load(connection, code)
            member(room, guest.id)
            response(connection, touch(connection, room, guest.id, clock()), guest.id)
        }
    }

    fun command(token: String, code: String, request: CommandRequest): RoomUpdate {
        validId(request.id)
        demand(request.expectedRevision >= 0, 400, "invalid_revision", "Revision must be non-negative.")
        authenticatedRate(token, "command", 180)
        return database.transaction { connection ->
            // Take the profile write lock before room locks; do not upgrade a shared
            // profile lock while a concurrent command holds it and waits on this room.
            val guest = authenticate(connection, token, lock = request.action is RoomAction.ChooseAvatar)
            var room = load(connection, code, allowClosed = request.action == RoomAction.Leave)
            val hash = digest(WireJson.encodeToString(request))
            val receipt = connection.query("SELECT request_hash, response FROM command_receipts WHERE room_id = ? AND actor = ? AND command_id = ?", room.id, guest.id, request.id) { Receipt(it.getString(1), it.getString(2)) }.singleOrNull()
            if (request.action != RoomAction.Leave || receipt == null) member(room, guest.id)
            if (receipt != null) {
                demand(receipt.hash == hash, 409, "id_reused", "This command ID was already used for another request.")
                return@transaction WireJson.decodeFromString<RoomUpdate>(receipt.response)
            }
            demand(room.expiresAt > clock() && room.phase != RoomPhase.CLOSED, 410, "room_closed", "This room has closed or expired.")
            // A claim is tied to a round/call, not to unrelated readiness/presence or
            // another same-call winner. Future revisions are still invalid.
            demand(room.revision == request.expectedRevision || ((request.action is RoomAction.Claim || request.action is RoomAction.Mark || request.action is RoomAction.UsePower) && request.expectedRevision < room.revision),
                409, "stale_revision", "The room changed. Refresh before trying again.")
            val now = clock()
            room = room.copy(members = room.members.map { if (it.id == guest.id) it.copy(lastSeen = now, connected = true) else it })
            val next = apply(room, guest.id, request.action, now)
            (request.action as? RoomAction.ChooseAvatar)?.let { connection.execute("UPDATE guests SET avatar = ? WHERE id = ?", it.avatar, guest.id) }
            val saved = changed(connection, next, actionName(request.action), now)
            val response = update(connection, saved, guest.id, request.expectedRevision)
            connection.execute("INSERT INTO command_receipts VALUES (?, ?, ?, ?, ?)", room.id, guest.id, request.id, hash, WireJson.encodeToString(response))
            response
        }
    }

    /** Short row locks are scheduler ownership; SKIP LOCKED makes concurrent workers safe. */
    fun tick(): Int = database.transaction { connection ->
        val now = clock()
        val rooms = connection.query("SELECT payload FROM rooms WHERE id IN (SELECT id FROM rooms WHERE phase <> 'CLOSED' ORDER BY checked_at, id LIMIT 64) ORDER BY id FOR UPDATE SKIP LOCKED") { decode(it.getString(1)) }
        rooms.forEach { original ->
            var room = original
            var event = "presence"
            if (room.expiresAt <= now) {
                room = room.copy(phase = RoomPhase.CLOSED, round = room.round?.cancel(), nextDrawAt = null, computerClaimsAt = emptyMap())
                event = "expired"
            } else {
                val present = room.members.map { it.copy(connected = now - it.lastSeen < PRESENCE_TIMEOUT) }
                room = room.copy(members = present)
                if (present.none { it.id == room.hostId && it.connected }) {
                    present.filter { it.connected }.minWithOrNull(compareBy<Member> { it.joinedAt }.thenBy { it.id })?.let {
                        room = room.copy(hostId = it.id)
                        event = "host_changed"
                    }
                }
                if (room.phase == RoomPhase.LOBBY && room.options.powersEnabled && !room.friendTable) {
                    val joined = ((now - (requireNotNull(room.startsAt) - MATCH_COUNTDOWN)) / 3_000L).toInt().coerceIn(0, 3)
                    if (joined > room.practiceSeats) { room = room.copy(practiceSeats = joined).coinLobby(); event = "practice_joined" }
                }
                if (room.phase == RoomPhase.LOBBY && room.options.coinGame && room.startsAt?.let { it <= now } == true) {
                    // A long host outage refunds the queue instead of spending
                    // entries after players believe the countdown failed.
                    room = if (now - requireNotNull(room.startsAt) > 30_000L || room.members.isEmpty())
                        room.copy(phase = RoomPhase.CLOSED, startsAt = null)
                    else room.startCoinRound(now)
                    event = if (room.phase == RoomPhase.CLOSED) "queue_refunded" else "started"
                } else if (room.phase == RoomPhase.ACTIVE && room.nextDrawAt?.let { it <= now } == true) {
                    room = draw(room, now)
                    event = "drawn"
                } else if (room.phase == RoomPhase.ACTIVE && room.round?.status == RoundStatus.PLAYING) {
                    val due = room.computerClaimsAt.filterValues { it <= now }.keys
                    if (due.isNotEmpty()) {
                        var game = requireNotNull(room.round)
                        due.forEach { game = game.claimComputer(it) }
                        room = room.copy(round = game, computerClaimsAt = room.computerClaimsAt - due)
                        event = "computer_claimed"
                    }
                }
            }
            if (room != original) changed(connection, room, event, now)
            connection.execute("UPDATE rooms SET checked_at = ? WHERE id = ?", now, room.id)
        }
        rooms.size
    }

    fun cleanup() = database.transaction { connection ->
        val now = clock()
        connection.execute("DELETE FROM guests WHERE expires_at < ? AND (device_key_hash IS NULL OR revoked_at IS NOT NULL)", now - 30 * ROOM_LIFETIME)
        connection.execute("DELETE FROM rooms WHERE id IN (SELECT id FROM rooms WHERE expires_at < ? ORDER BY id FOR UPDATE)", now - 30 * ROOM_LIFETIME)
        connection.execute("DELETE FROM deletion_receipts WHERE expires_at <= ?", now)
        connection.execute("DELETE FROM rate_limits WHERE window_start < ?", now / 60_000 - 2)
        Unit
    }

    private fun apply(room: RoomRecord, actor: String, action: RoomAction, now: Long): RoomRecord {
        fun host() = demand(room.hostId == actor, 403, "host_only", "Only the host can do that.")
        fun lobby() = demand(room.phase == RoomPhase.LOBBY, 409, "not_lobby", "Settings and membership are locked during a round.")
        fun active() = demand(room.phase == RoomPhase.ACTIVE, 409, "not_active", "There is no active round.")
        if (room.options.coinGame) demand(action is RoomAction.Claim || action is RoomAction.Mark || action is RoomAction.UsePower || action is RoomAction.BuyTickets || action == RoomAction.Leave || (room.friendTable && action == RoomAction.Start),
            403, "automatic_coin_round", "Coin rounds are managed by the server.")
        return when (action) {
            is RoomAction.Mark -> {
                active()
                val game = requireNotNull(room.round)
                demand(room.options.powersEnabled && game.id == action.roundId && game.status == RoundStatus.PLAYING,
                    409, "power_round_changed", "Check the current round before marking.")
                val ticket = game.tickets.firstOrNull { it.id == action.ticketId && it.playerId == actor }
                demand(ticket != null && action.number in ticket.numbers && action.number in game.called,
                    422, "number_not_called", "That ticket number has not been called.")
                val powers = room.matchPowers[actor] ?: MatchPowers()
                demand(action.ticketId !in powers.discarded, 409, "ticket_discarded", "This ticket was discarded for this round.")
                val next = powers.mark(requireNotNull(ticket), action.number, game.called) {
                    MatchPower.entries[java.security.SecureRandom().nextInt(MatchPower.entries.size)]
                }
                room.copy(matchPowers = room.matchPowers + (actor to next))
            }
            is RoomAction.UsePower -> {
                active()
                val game = requireNotNull(room.round)
                demand(room.options.powersEnabled && game.id == action.roundId && game.status == RoundStatus.PLAYING,
                    409, "power_round_changed", "Check the current round before using a power.")
                val ticket = game.tickets.firstOrNull { it.id == action.ticketId && it.playerId == actor }
                val powers = room.matchPowers[actor] ?: MatchPowers()
                demand(ticket != null && action.ticketId !in powers.discarded && action.power != MatchPower.SHIELD &&
                    action.power in powers.inventory && action.power !in powers.used[action.ticketId].orEmpty(),
                    409, "power_unavailable", "That power cannot be used on this ticket.")
                room.copy(matchPowers = room.matchPowers + (actor to powers.activate(requireNotNull(ticket), action.power, game.called, now)))
            }
            is RoomAction.BuyTickets -> {
                lobby()
                demand(room.options.coinGame && (room.friendTable || now < requireNotNull(room.startsAt)), 409, "sales_closed", "Ticket sales have closed.")
                room.copy(purchases = room.purchases + (actor to action.quantity))
            }
            is RoomAction.Ready -> { lobby(); room.copy(members = room.members.map { if (it.id == actor) it.copy(ready = action.value) else it }) }
            is RoomAction.ChooseAvatar -> {
                lobby()
                demand(action.avatar in 0 until AVATAR_COUNT, 400, "invalid_avatar", "Choose a supported avatar.")
                room.copy(members = room.members.map { if (it.id == actor) it.copy(avatar = action.avatar, ready = false) else it })
            }
            is RoomAction.Configure -> {
                host(); lobby()
                demand(action.options.capacity >= room.members.size + action.options.computerPlayers, 409, "capacity", "Capacity cannot be smaller than the current group, including computers.")
                room.copy(options = action.options, members = room.members.map { it.copy(ready = false) })
            }
            is RoomAction.Lock -> { host(); lobby(); room.copy(locked = action.value) }
            RoomAction.Start -> {
                host(); lobby()
                val count = room.members.size + room.options.computerPlayers
                demand(count in 2..room.options.capacity && room.members.all { it.ready && now - it.lastSeen < PRESENCE_TIMEOUT },
                    409, "not_ready", "At least two seats are needed; every human member must be connected and ready.")
                if (room.friendTable) return room.startCoinRound(now)
                val houses = room.options.game.prizes.count { it.isRankedHouse }.coerceAtLeast(1)
                demand(count * room.options.game.ticketsPerPlayer >= houses,
                    409, "insufficient_tickets", "$houses houses need at least $houses tickets at the table. Add players or increase tickets per player.")
                val computers = (1..room.options.computerPlayers).map { index ->
                    computerPlayer(room.id, index)
                }
                val game = Round.create(room.members.map { Player(it.id, it.name, avatar = it.avatar) } + computers, room.options.game, now = now).start()
                val nonce = secret()
                room.copy(phase = RoomPhase.ACTIVE, locked = true, round = game, nonce = nonce, drawCommitment = commitment(game, nonce),
                    nextDrawAt = (now + room.options.intervalSeconds * 1000).takeIf { room.options.automaticCalling })
            }
            RoomAction.Draw -> {
                host(); active()
                demand(room.round?.status == RoundStatus.PLAYING, 409, "paused", "Resume the round before calling a number.")
                demand(!room.options.automaticCalling, 409, "automatic_calling", "The server calls numbers automatically in this room.")
                draw(room, now)
            }
            is RoomAction.Claim -> {
                active()
                val game = requireNotNull(room.round)
                demand(game.settings.manualClaims, 409, "automatic_awards", "This round awards prizes automatically.")
                demand(action.roundId == game.id, 409, "claim_round_changed", "This claim belongs to another round.")
                demand(action.drawIndex == game.called.size, 409, "claim_window_closed", "That call has ended. Check the current number before claiming again.")
                val owned = game.tickets.filter { it.playerId == actor }.flatMap { it.numbers }.toSet()
                demand(action.markedNumbers.all { it in game.called && it in owned }, 400, "invalid_claim_marks", "Claims can use only your called ticket numbers.")
                demand(game.tickets.any { it.id == action.selection.ticketId && it.playerId == actor } &&
                    (game.settings.prizes.any { it.name == action.selection.prizeId } || game.settings.customPrizes.any { it.id == action.selection.prizeId }),
                    400, "invalid_claim_selection", "Choose an owned ticket and an enabled prize.")
                if (room.options.powersEnabled) return room.powerClaim(actor, action)
                val claimed = game.claim(actor, action.markedNumbers, action.selection)
                demand(claimed != game, 422, "no_valid_claim", "No new prize matches your marked numbers yet.")
                room.copy(round = claimed)
            }
            RoomAction.Pause -> { host(); active(); room.copy(round = room.round!!.pause(), nextDrawAt = null, computerClaimsAt = emptyMap()) }
            RoomAction.Resume -> {
                host(); active()
                demand(room.round?.status == RoundStatus.PAUSED, 409, "not_paused", "This round is not paused.")
                val game = room.round!!.start()
                room.copy(round = game, nextDrawAt = (now + room.options.intervalSeconds * 1000).takeIf { room.options.automaticCalling },
                    computerClaimsAt = game.computerClaimDelays().mapValues { now + it.value })
            }
            RoomAction.End -> { host(); active(); room.copy(phase = RoomPhase.FINISHED, round = room.round!!.cancel(), nextDrawAt = null, computerClaimsAt = emptyMap()) }
            RoomAction.Rematch -> {
                host()
                demand(room.phase == RoomPhase.FINISHED, 409, "not_finished", "Finish this round before starting a rematch.")
                room.copy(phase = RoomPhase.LOBBY, locked = false, round = null, nonce = null, drawCommitment = null, nextDrawAt = null, computerClaimsAt = emptyMap(),
                    members = room.members.map { it.copy(ready = false) })
            }
            is RoomAction.Remove -> {
                host(); lobby()
                demand(actor != action.playerId && room.members.any { it.id == action.playerId }, 400, "invalid_member", "Choose another member of this lobby.")
                room.copy(members = room.members.filterNot { it.id == action.playerId })
            }
            RoomAction.Leave -> {
                demand(room.phase != RoomPhase.ACTIVE, 409, "round_in_progress", "You can disconnect and rejoin. Leave the group after the round ends.")
                val remaining = room.members.filterNot { it.id == actor }
                room.copy(members = remaining, phase = if (remaining.isEmpty()) RoomPhase.CLOSED else room.phase,
                    purchases = if (room.phase == RoomPhase.LOBBY) room.purchases - actor else room.purchases,
                    powerUps = if (room.phase == RoomPhase.LOBBY) room.powerUps - actor else room.powerUps,
                    hostId = if (room.hostId == actor) remaining.firstOrNull()?.id ?: actor else room.hostId)
            }
        }
    }

    private fun draw(room: RoomRecord, now: Long): RoomRecord {
        val game = requireNotNull(room.round).draw()
        return room.copy(round = game, phase = if (game.finished) RoomPhase.FINISHED else RoomPhase.ACTIVE,
            matchPowers = room.matchPowers.mapValues { (actor, powers) ->
                if (game.finished) powers else powers.autoMark(game.tickets.filter { it.playerId == actor }, game.called, now)
            },
            nextDrawAt = (now + room.options.intervalSeconds * 1000).takeIf { !game.finished && room.options.automaticCalling },
            computerClaimsAt = game.computerClaimDelays().mapValues { now + it.value })
    }

    private fun authenticate(connection: Connection, token: String, lock: Boolean = false, protect: Boolean = true): Guest {
        validToken(token)
        val saved = journal?.let { recoveryPosition(connection) }
        val guest = connection.query("SELECT id, name, avatar FROM guests WHERE token_hash = ? AND expires_at > ? AND revoked_at IS NULL${if (lock) " FOR UPDATE" else if (protect) " FOR SHARE" else ""}", digest(token), clock()) {
            Guest(it.getString(1), it.getString(2), it.getInt(3))
        }.singleOrNull()
        if (journal != null) {
            val access = journal.accessState(guest?.id)
            verifyJournalPosition(saved, access.position)
            demand(!access.blocked, 401, "unauthorized", "This profile's credentials were revoked.")
        }
        return guest ?: fail(401, "unauthorized", "This guest session has expired or was revoked.")
    }

    private fun load(connection: Connection, code: String, lock: Boolean = true, allowClosed: Boolean = false): RoomRecord {
        demand(code.matches(Regex("[A-Z2-9]{8}")), 404, "room_missing", "Room not found.")
        val room = connection.query("SELECT payload FROM rooms WHERE code = ?${if (lock) " FOR UPDATE" else ""}", code) { decode(it.getString(1)) }.singleOrNull()
            ?: fail(404, "room_missing", "Room not found.")
        demand(allowClosed || (room.expiresAt > clock() && room.phase != RoomPhase.CLOSED), 410, "room_closed", "This room has closed or expired.")
        return room
    }

    private fun member(room: RoomRecord, actor: String) = demand(room.members.any { it.id == actor }, 403, "not_member", "Join this room before viewing it.")

    private fun needsTouch(room: RoomRecord, actor: String, now: Long): Boolean {
        val member = room.members.first { it.id == actor }
        return now - member.lastSeen >= TOUCH_INTERVAL || !member.connected
    }

    private fun touch(connection: Connection, room: RoomRecord, actor: String, now: Long): RoomRecord {
        if (!needsTouch(room, actor, now)) return room
        val member = room.members.first { it.id == actor }
        val updated = room.copy(members = room.members.map { if (it.id == actor) it.copy(lastSeen = now, connected = true) else it })
        return if (!member.connected) changed(connection, updated, "reconnected", now) else updated.also { save(connection, it) }
    }

    private fun changed(connection: Connection, room: RoomRecord, type: String, now: Long, before: RoomRecord? = null): RoomRecord {
        val next = room.coinLobby().copy(revision = room.revision + 1)
        if (next.options.coinGame) {
            val previous = before ?: connection.query("SELECT payload FROM rooms WHERE id = ?", next.id) { decode(it.getString(1)) }.single()
            RoomEconomy.reconcile(connection, previous, next, now)
            RoomEconomy.settle(connection, next, now)
        }
        save(connection, next)
        val event = RoomEvent(next.revision, type, now, next.round?.id)
        connection.execute("INSERT INTO room_events VALUES (?, ?, ?)", next.id, next.revision, WireJson.encodeToString(event))
        if (next.revision > EVENT_LIMIT)
            connection.execute("DELETE FROM room_events WHERE room_id = ? AND revision <= ?", next.id, next.revision - EVENT_LIMIT)
        if (next.round?.finished == true) connection.execute("INSERT INTO finished_rounds VALUES (?, ?, ?) ON CONFLICT DO NOTHING", next.id, next.round.id, WireJson.encodeToString(next))
        return next
    }

    private fun save(connection: Connection, room: RoomRecord) {
        connection.execute("UPDATE rooms SET phase = ?, expires_at = ?, payload = ? WHERE id = ?", room.phase.name, room.expiresAt, WireJson.encodeToString(room), room.id)
        val ids = (room.members.map { it.id } + room.round?.players.orEmpty().map { it.id }).distinct()
        val array = connection.createArrayOf("text", ids.toTypedArray())
        try { connection.execute("INSERT INTO room_participants SELECT ?, id FROM guests WHERE id = ANY(?) ON CONFLICT DO NOTHING", room.id, array) }
        finally { array.free() }
    }

    private fun update(connection: Connection, room: RoomRecord, actor: String, after: Long?): RoomUpdate {
        demand(after == null || after >= 0, 400, "invalid_cursor", "Event cursor must be non-negative.")
        val resync = after == null || after > room.revision || after < maxOf(0, room.revision - EVENT_LIMIT)
        val events = if (resync) emptyList() else connection.query("SELECT payload FROM room_events WHERE room_id = ? AND revision > ? AND revision <= ? ORDER BY revision", room.id, after, room.revision) { WireJson.decodeFromString<RoomEvent>(it.getString(1)) }
        return RoomUpdate(room.view(actor, clock()).let { view ->
            if (room.options.coinGame) view.copy(wallet = CoinLedger.view(connection, actor)) else view
        }, events, resync)
    }

    private fun authenticatedRate(token: String, operation: String, maximum: Int) = database.transaction { connection ->
        val guest = authenticate(connection, token)
        rate(connection, "$operation:${guest.id}", maximum)
    }

    private fun rate(key: String, maximum: Int) = database.transaction { rate(it, key, maximum) }

    private fun rate(connection: Connection, key: String, maximum: Int) {
        val count = connection.query("INSERT INTO rate_limits VALUES (?, ?, 1) ON CONFLICT (bucket, window_start) DO UPDATE SET requests = rate_limits.requests + 1 RETURNING requests", key, clock() / 60_000) { it.getInt(1) }.single()
        demand(count <= maximum, 429, "rate_limited", "Too many requests. Try again in a minute.")
    }

    private fun validId(id: String) = demand(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false), 400, "invalid_id", "Use a canonical UUID for each new command.")
    private fun validToken(token: String) = demand(token.matches(Regex("[A-Za-z0-9_-]{43}")), 401, "unauthorized", "A valid guest session is required.")
    private fun decode(payload: String): RoomRecord = WireJson.decodeFromString<RoomRecord>(payload).let { record ->
        record.copy(round = record.round?.let { game ->
            require(game.version in 1..6) { "Unsupported stored round format" }
            game.copy(version = maxOf(3, game.version))
        })
    }
    private fun actionName(action: RoomAction): String = when (action) {
        is RoomAction.Mark -> "marked"
        is RoomAction.UsePower -> "power_used"
        is RoomAction.BuyTickets -> "tickets_bought"
        is RoomAction.ChooseAvatar -> "avatar_changed"
        is RoomAction.Claim -> "claimed"
        is RoomAction.Ready -> "ready"; is RoomAction.Configure -> "configured"; is RoomAction.Lock -> "locked"
        RoomAction.Start -> "started"; RoomAction.Draw -> "drawn"; RoomAction.Pause -> "paused"; RoomAction.Resume -> "resumed"
        RoomAction.End -> "ended"; RoomAction.Rematch -> "rematch"; is RoomAction.Remove -> "removed"; RoomAction.Leave -> "left"
    }
}
