package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import java.sql.Connection
import java.util.UUID

/** All accepted state transitions, receipts and public events commit in one PostgreSQL transaction. */
class RoomService(private val database: Database, private val clock: () -> Long = System::currentTimeMillis) {
    fun register(request: GuestRequest, source: String): GuestCredentials {
        val name = request.displayName.trim()
        demand(name.length in 1..40 && name.none(Char::isISOControl) && request.avatar in 0..7,
            400, "invalid_guest", "Use a name of 1–40 characters and a supported avatar.")
        rate("guest:${digest(source)}", 60)
        val token = secret()
        val credentials = GuestCredentials(UUID.randomUUID().toString(), token, clock() + SESSION_LIFETIME)
        database.transaction { it.execute("INSERT INTO guests (id, name, avatar, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)", credentials.playerId, name, request.avatar, digest(token), credentials.expiresAt) }
        return credentials
    }

    fun revoke(token: String) = database.transaction { connection ->
        val guest = authenticate(connection, token, lock = true)
        connection.execute("UPDATE guests SET expires_at = ?, revoked_at = ? WHERE id = ?", clock(), clock(), guest.id)
        Unit
    }

    /** Profile removal and shared-record redaction are atomic with a retryable confirmation. */
    fun deleteProfile(token: String, request: DeleteProfileRequest, source: String): DeleteProfileReceipt {
        validId(request.id)
        validToken(token)
        rate("delete:${digest(source)}", 30)
        val proof = digest("tambola-delete-v1\n$token\n${request.id}")
        return database.transaction { connection ->
            fun receipt() = connection.query("SELECT deleted_at, expires_at FROM deletion_receipts WHERE confirmation_hash = ? AND expires_at > ?", proof, clock()) {
                DeleteProfileReceipt(request.id, it.getLong(1), it.getLong(2))
            }.singleOrNull()
            receipt()?.let { return@transaction it }
            // An expired but unrevoked token may delete its own profile, never read/play again.
            val guest = connection.query("SELECT id, name, avatar FROM guests WHERE token_hash = ? AND revoked_at IS NULL FOR UPDATE", digest(token)) {
                Guest(it.getString(1), it.getString(2), it.getInt(3))
            }.singleOrNull() ?: return@transaction (receipt() ?: fail(401, "unauthorized", "Deletion could not be confirmed with this session. Local reset does not delete server data."))
            val now = clock()
            val rooms = connection.query("SELECT payload FROM rooms WHERE id IN (SELECT room_id FROM room_participants WHERE player_id = ?) ORDER BY id FOR UPDATE", guest.id) { decode(it.getString(1)) }
            rooms.forEach { original ->
                val redacted = original.redact(guest.id)
                val remaining = redacted.members.filterNot { it.id == guest.id }
                val successor = remaining.minWithOrNull(compareBy<Member> { now - it.lastSeen >= PRESENCE_TIMEOUT }.thenBy { it.joinedAt }.thenBy { it.id })
                val next = redacted.copy(members = remaining,
                    hostId = if (original.hostId == guest.id) successor?.id.orEmpty() else original.hostId,
                    phase = if (remaining.isEmpty()) RoomPhase.CLOSED else original.phase,
                    round = if (remaining.isEmpty()) redacted.round?.cancel() else redacted.round,
                    nextDrawAt = if (remaining.isEmpty()) null else redacted.nextDrawAt)
                changed(connection, next, "profile_deleted", now)
                connection.query("SELECT round_id, payload FROM finished_rounds WHERE room_id = ? FOR UPDATE", original.id) { it.getString(1) to decode(it.getString(2)) }.forEach { (id, archive) ->
                    connection.execute("UPDATE finished_rounds SET payload = ? WHERE room_id = ? AND round_id = ?", WireJson.encodeToString(archive.redact(guest.id)), original.id, id)
                }
                connection.query("SELECT actor, command_id, response FROM command_receipts WHERE room_id = ? AND actor <> ? FOR UPDATE", original.id, guest.id) {
                    Triple(it.getString(1), it.getString(2), WireJson.decodeFromString<RoomUpdate>(it.getString(3)))
                }.forEach { (actor, id, response) ->
                    connection.execute("UPDATE command_receipts SET response = ? WHERE room_id = ? AND actor = ? AND command_id = ?", WireJson.encodeToString(response.redact(guest.id)), original.id, actor, id)
                }
            }
            connection.execute("DELETE FROM command_receipts WHERE actor = ?", guest.id)
            connection.execute("DELETE FROM room_participants WHERE player_id = ?", guest.id)
            listOf("create", "join", "read", "command").forEach { connection.execute("DELETE FROM rate_limits WHERE bucket = ?", "$it:${guest.id}") }
            connection.execute("DELETE FROM guests WHERE id = ?", guest.id)
            val result = DeleteProfileReceipt(request.id, now, now + 30 * ROOM_LIFETIME)
            connection.execute("INSERT INTO deletion_receipts VALUES (?, ?, ?)", proof, result.deletedAt, result.confirmUntil)
            result
        }
    }

    fun create(token: String, request: CreateRoomRequest): RoomUpdate {
        validId(request.id)
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
                demand(room.phase == RoomPhase.LOBBY && !room.locked, 409, "room_locked", "This room is not accepting new players.")
                demand(room.members.size < room.options.capacity, 409, "room_full", "This room is full.")
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

    fun command(token: String, code: String, request: CommandRequest): RoomUpdate {
        validId(request.id)
        demand(request.expectedRevision >= 0, 400, "invalid_revision", "Revision must be non-negative.")
        authenticatedRate(token, "command", 180)
        return database.transaction { connection ->
            val guest = authenticate(connection, token)
            var room = load(connection, code)
            val hash = digest(WireJson.encodeToString(request))
            val receipt = connection.query("SELECT request_hash, response FROM command_receipts WHERE room_id = ? AND actor = ? AND command_id = ?", room.id, guest.id, request.id) { Receipt(it.getString(1), it.getString(2)) }.singleOrNull()
            if (request.action != RoomAction.Leave || receipt == null) member(room, guest.id)
            if (receipt != null) {
                demand(receipt.hash == hash, 409, "id_reused", "This command ID was already used for another request.")
                return@transaction WireJson.decodeFromString<RoomUpdate>(receipt.response)
            }
            demand(room.revision == request.expectedRevision, 409, "stale_revision", "The room changed. Refresh before trying again.")
            val now = clock()
            room = room.copy(members = room.members.map { if (it.id == guest.id) it.copy(lastSeen = now, connected = true) else it })
            val next = apply(room, guest.id, request.action, now)
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
                room = room.copy(phase = RoomPhase.CLOSED, round = room.round?.cancel(), nextDrawAt = null)
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
                if (room.phase == RoomPhase.ACTIVE && room.nextDrawAt?.let { it <= now } == true) {
                    room = draw(room, now)
                    event = "drawn"
                }
            }
            if (room != original) changed(connection, room, event, now)
            connection.execute("UPDATE rooms SET checked_at = ? WHERE id = ?", now, room.id)
        }
        rooms.size
    }

    fun cleanup() = database.transaction { connection ->
        val now = clock()
        connection.execute("DELETE FROM guests WHERE expires_at < ?", now - 30 * ROOM_LIFETIME)
        connection.execute("DELETE FROM rooms WHERE id IN (SELECT id FROM rooms WHERE expires_at < ? ORDER BY id FOR UPDATE)", now - 30 * ROOM_LIFETIME)
        connection.execute("DELETE FROM deletion_receipts WHERE expires_at <= ?", now)
        connection.execute("DELETE FROM rate_limits WHERE window_start < ?", now / 60_000 - 2)
        Unit
    }

    private fun apply(room: RoomRecord, actor: String, action: RoomAction, now: Long): RoomRecord {
        fun host() = demand(room.hostId == actor, 403, "host_only", "Only the host can do that.")
        fun lobby() = demand(room.phase == RoomPhase.LOBBY, 409, "not_lobby", "Settings and membership are locked during a round.")
        fun active() = demand(room.phase == RoomPhase.ACTIVE, 409, "not_active", "There is no active round.")
        return when (action) {
            is RoomAction.Ready -> { lobby(); room.copy(members = room.members.map { if (it.id == actor) it.copy(ready = action.value) else it }) }
            is RoomAction.Configure -> {
                host(); lobby()
                demand(action.options.capacity >= room.members.size, 409, "capacity", "Capacity cannot be smaller than the current group.")
                room.copy(options = action.options, members = room.members.map { it.copy(ready = false) })
            }
            is RoomAction.Lock -> { host(); lobby(); room.copy(locked = action.value) }
            RoomAction.Start -> {
                host(); lobby()
                demand(room.members.size >= 2 && room.members.all { it.ready && now - it.lastSeen < PRESENCE_TIMEOUT },
                    409, "not_ready", "At least two connected players must be ready; every member must be ready.")
                val houses = room.options.game.prizes.count { it.isRankedHouse }.coerceAtLeast(1)
                demand(room.members.size * room.options.game.ticketsPerPlayer >= houses,
                    409, "insufficient_tickets", "$houses houses need at least $houses tickets at the table. Add players or increase tickets per player.")
                val game = Round.create(room.members.map { Player(it.id, it.name) }, room.options.game, now = now).start()
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
            RoomAction.Pause -> { host(); active(); room.copy(round = room.round!!.pause(), nextDrawAt = null) }
            RoomAction.Resume -> {
                host(); active()
                demand(room.round?.status == RoundStatus.PAUSED, 409, "not_paused", "This round is not paused.")
                room.copy(round = room.round!!.start(), nextDrawAt = (now + room.options.intervalSeconds * 1000).takeIf { room.options.automaticCalling })
            }
            RoomAction.End -> { host(); active(); room.copy(phase = RoomPhase.FINISHED, round = room.round!!.cancel(), nextDrawAt = null) }
            RoomAction.Rematch -> {
                host()
                demand(room.phase == RoomPhase.FINISHED, 409, "not_finished", "Finish this round before starting a rematch.")
                room.copy(phase = RoomPhase.LOBBY, locked = false, round = null, nonce = null, drawCommitment = null, nextDrawAt = null,
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
                    hostId = if (room.hostId == actor) remaining.firstOrNull()?.id ?: actor else room.hostId)
            }
        }
    }

    private fun draw(room: RoomRecord, now: Long): RoomRecord {
        val game = requireNotNull(room.round).draw()
        return room.copy(round = game, phase = if (game.finished) RoomPhase.FINISHED else RoomPhase.ACTIVE,
            nextDrawAt = (now + room.options.intervalSeconds * 1000).takeIf { !game.finished && room.options.automaticCalling })
    }

    private fun authenticate(connection: Connection, token: String, lock: Boolean = false): Guest {
        validToken(token)
        return connection.query("SELECT id, name, avatar FROM guests WHERE token_hash = ? AND expires_at > ? AND revoked_at IS NULL${if (lock) " FOR UPDATE" else " FOR SHARE"}", digest(token), clock()) {
            Guest(it.getString(1), it.getString(2), it.getInt(3))
        }.singleOrNull() ?: fail(401, "unauthorized", "This guest session has expired or was revoked.")
    }

    private fun load(connection: Connection, code: String): RoomRecord {
        demand(code.matches(Regex("[A-Z2-9]{8}")), 404, "room_missing", "Room not found.")
        val room = connection.query("SELECT payload FROM rooms WHERE code = ? FOR UPDATE", code) { decode(it.getString(1)) }.singleOrNull()
            ?: fail(404, "room_missing", "Room not found.")
        demand(room.expiresAt > clock() && room.phase != RoomPhase.CLOSED, 410, "room_closed", "This room has closed or expired.")
        return room
    }

    private fun member(room: RoomRecord, actor: String) = demand(room.members.any { it.id == actor }, 403, "not_member", "Join this room before viewing it.")

    private fun touch(connection: Connection, room: RoomRecord, actor: String, now: Long): RoomRecord {
        val member = room.members.first { it.id == actor }
        if (now - member.lastSeen < TOUCH_INTERVAL && member.connected) return room
        val updated = room.copy(members = room.members.map { if (it.id == actor) it.copy(lastSeen = now, connected = true) else it })
        return if (!member.connected) changed(connection, updated, "reconnected", now) else updated.also { save(connection, it) }
    }

    private fun changed(connection: Connection, room: RoomRecord, type: String, now: Long): RoomRecord {
        val next = room.copy(revision = room.revision + 1)
        save(connection, next)
        val event = RoomEvent(next.revision, type, now, next.round?.id)
        connection.execute("INSERT INTO room_events VALUES (?, ?, ?)", next.id, next.revision, WireJson.encodeToString(event))
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
        val events = if (resync) emptyList() else connection.query("SELECT payload FROM room_events WHERE room_id = ? AND revision > ? ORDER BY revision", room.id, after) { WireJson.decodeFromString<RoomEvent>(it.getString(1)) }
        return RoomUpdate(room.view(actor, clock()), events, resync)
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
    private fun decode(payload: String): RoomRecord = WireJson.decodeFromString(payload)
    private fun actionName(action: RoomAction): String = when (action) {
        is RoomAction.Ready -> "ready"; is RoomAction.Configure -> "configured"; is RoomAction.Lock -> "locked"
        RoomAction.Start -> "started"; RoomAction.Draw -> "drawn"; RoomAction.Pause -> "paused"; RoomAction.Resume -> "resumed"
        RoomAction.End -> "ended"; RoomAction.Rematch -> "rematch"; is RoomAction.Remove -> "removed"; RoomAction.Leave -> "left"
    }
}
