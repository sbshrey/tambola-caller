package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.serialization.encodeToString
import java.sql.Connection
import java.util.UUID

/** Uses the existing authentication/deletion barrier and ledger; all room changes and receipts commit together. */
internal class BingoRoomService(
    private val database: Database, private val clock: () -> Long,
    private val authenticate: (Connection, String, Boolean) -> Guest,
    private val rate: (String, String, Int) -> Unit,
) {
    fun match(token: String, request: BingoMatchRequest): BingoRoomView {
        validId(request.id); rate(token, "bingo_match", 20)
        return database.transaction { connection ->
            val guest = authenticate(connection, token, true)
            val hash = digest("bingo:match:" + WireJson.encodeToString(request))
            receipt(connection, guest.id, request.id, hash)?.let { return@transaction it }
            val now = clock()
            val existing = connection.query("""SELECT payload FROM bingo_rooms WHERE phase IN ('LOBBY','ACTIVE') AND expires_at > ?
                AND id IN (SELECT room_id FROM bingo_participants WHERE player_id = ?) ORDER BY id FOR UPDATE""", now, guest.id) {
                decode(it.getString(1))
            }.firstOrNull { room -> room.members.any { it.id == guest.id } }
            val saved = if (existing != null) existing else {
                demand(!hasTambolaRoom(connection, guest.id, now), 409, "other_game_active", "Finish or leave your Tambola table before joining Bingo.")
                CoinLedger.ensure(connection, guest.id, now)
                if (!request.friendTable) connection.query("SELECT pg_advisory_xact_lock(749023810)") { true }
                val at = clock()
                val waiting = when {
                    request.friendCode != null -> load(connection, requireNotNull(request.friendCode)).also {
                        demand(it.friendTable && it.phase == RoomPhase.LOBBY && it.members.size < 50,
                            409, "bingo_table_closed", "This Bingo table is not accepting players.")
                        demand(it.quickPlay == request.quickPlay, 409, "bingo_mode_mismatch", "Update the app to join this Bingo table.")
                    }
                    request.friendTable -> null
                    else -> connection.query("""SELECT payload FROM bingo_rooms WHERE phase = 'LOBBY' AND NOT friend_table
                        AND starts_at > ? AND expires_at > ? AND jsonb_array_length(payload::jsonb->'members') < 50
                        AND coalesce((payload::jsonb->>'realPlayersOnly')::boolean, false) = ${request.realPlayersOnly}
                        AND coalesce((payload::jsonb->>'quickPlay')::boolean, false) = ${request.quickPlay}
                        ORDER BY starts_at, id LIMIT 1 FOR UPDATE""", at, at) { decode(it.getString(1)) }.singleOrNull()
                }
                val room = waiting ?: BingoRoomRecord(UUID.randomUUID().toString(), "B-" + roomCode(), guest.id,
                    emptyList(), emptyMap(), at, at + if (request.friendTable) FRIEND_LOBBY_LIFETIME else ROOM_LIFETIME,
                    friendTable = request.friendTable, startsAt = (at + MATCH_COUNTDOWN).takeUnless { request.friendTable },
                    realPlayersOnly = request.realPlayersOnly, quickPlay = request.quickPlay).also {
                    connection.execute("INSERT INTO bingo_rooms (id, code, phase, expires_at, starts_at, friend_table, payload) VALUES (?, ?, ?, ?, ?, ?, ?)",
                        it.id, it.code, it.phase.name, it.expiresAt, it.startsAt, it.friendTable, WireJson.encodeToString(it))
                }
                demand(room.expiresAt > clock() && (room.friendTable || requireNotNull(room.startsAt) > clock()),
                    409, "sales_closed", "The round is starting. Try Play again.")
                changed(connection, room, room.copy(members = room.members + Member(guest.id, guest.name, guest.avatar, at, ready = true, lastSeen = at),
                    purchases = room.purchases + (guest.id to request.cards)), at)
            }
            val response = view(connection, saved, guest.id)
            recordReceipt(connection, guest.id, request.id, hash, response)
            response
        }
    }
    fun read(token: String, code: String): BingoRoomView {
        rate(token, "bingo_read", 180)
        return database.transaction { connection ->
            val guest = authenticate(connection, token, false)
            val room = load(connection, code)
            member(room, guest.id)
            val updated = if (clock() - room.members.first { it.id == guest.id }.lastSeen < TOUCH_INTERVAL) room else
                room.copy(members = room.members.map { if (it.id == guest.id) it.copy(lastSeen = clock()) else it }).also { save(connection, it) }
            view(connection, updated, guest.id)
        }
    }
    fun command(token: String, code: String, request: BingoCommandRequest): BingoRoomView {
        validId(request.id); rate(token, "bingo_command", 240)
        return database.transaction { connection ->
            val guest = authenticate(connection, token, true)
            val hash = digest("bingo:command:$code:" + WireJson.encodeToString(request))
            receipt(connection, guest.id, request.id, hash)?.let { return@transaction it }
            val room = load(connection, code)
            member(room, guest.id)
            demand(room.revision == request.expectedRevision ||
                ((request.action is BingoAction.Mark || request.action is BingoAction.Claim) && request.expectedRevision < room.revision),
                409, "room_changed", "The Bingo table changed. Refresh and try again.")
            val now = clock()
            val next = when (val action = request.action) {
                BingoAction.Start -> {
                    demand(room.friendTable && room.hostId == guest.id, 403, "host_only", "Only the friends-table host can start this round.")
                    demand(room.phase == RoomPhase.LOBBY && room.members.size >= 2, 409, "bingo_not_ready", "Wait for at least two players.")
                    room.start(now)
                }
                BingoAction.Leave -> {
                    demand(room.phase == RoomPhase.LOBBY, 409, "bingo_already_started", "This Bingo round has already started.")
                    val members = room.members.filterNot { it.id == guest.id }
                    room.copy(members = members, purchases = room.purchases - guest.id,
                        hostId = if (room.hostId == guest.id) members.firstOrNull()?.id.orEmpty() else room.hostId,
                        phase = if (members.isEmpty()) RoomPhase.CLOSED else RoomPhase.LOBBY,
                        startsAt = room.startsAt.takeIf { members.isNotEmpty() })
                }
                is BingoAction.Mark -> {
                    val game = ownedGame(room, guest.id, action.roundId, action.cardId)
                    demand(action.number in game.draw.called && action.number in game.cards.first { it.id == action.cardId }.numbers,
                        422, "bingo_not_called", "Mark a called number on your card.")
                    room.copy(round = game.mark(guest.id, action.cardId, action.number))
                }
                is BingoAction.Claim -> {
                    val game = ownedGame(room, guest.id, action.roundId, action.cardId)
                    val card = game.cards.first { it.id == action.cardId }
                    val already = game.claims.any { it.playerId == guest.id && it.pattern == action.pattern }
                    demand(already || (!game.closed(action.pattern) && action.pattern.isComplete(card, game.draw.called.toSet(), game.marks[card.id].orEmpty())),
                        422, "bingo_claim_incomplete", "Complete the pattern before claiming an available prize.")
                    room.copy(round = game.claim(guest.id, action.cardId, action.pattern))
                }
            }
            val saved = changed(connection, room, next, now)
            val response = view(connection, saved, guest.id)
            recordReceipt(connection, guest.id, request.id, hash, response)
            response
        }
    }
    private fun ownedGame(room: BingoRoomRecord, actor: String, roundId: String, cardId: String): BingoRound {
        demand(room.phase == RoomPhase.ACTIVE && room.round?.id == roundId, 409, "bingo_round_changed", "This Bingo round is no longer active.")
        val game = requireNotNull(room.round)
        demand(game.cards.any { it.id == cardId && it.playerId == actor }, 403, "not_card_owner", "Use your own Bingo card.")
        return game
    }
    fun tick(connection: Connection, now: Long): Int {
        val rooms = connection.query("""SELECT payload FROM bingo_rooms WHERE id IN
            (SELECT id FROM bingo_rooms WHERE phase <> 'CLOSED' ORDER BY checked_at, id LIMIT 64)
            ORDER BY id FOR UPDATE SKIP LOCKED""") { decode(it.getString(1)) }
        for (room in rooms) {
            val next = room.tick(now)
            if (next != room) changed(connection, room, next, now)
            connection.execute("UPDATE bingo_rooms SET checked_at = ? WHERE id = ?", now, room.id)
        }
        return rooms.size
    }
    private fun changed(connection: Connection, before: BingoRoomRecord, after: BingoRoomRecord, now: Long): BingoRoomRecord {
        val next = after.copy(revision = before.revision + 1)
        if (before.phase == RoomPhase.LOBBY) {
            val purchases = if (next.phase == RoomPhase.CLOSED) emptyMap() else next.purchases
            (before.purchases.keys + purchases.keys).sorted().forEach { actor ->
                val delta = ((before.purchases[actor] ?: 0) - (purchases[actor] ?: 0)) * COIN_TICKET_PRICE
                val key = "bingo:${next.id}:purchase:${next.revision}:$actor"
                if (delta < 0) CoinLedger.change(connection, actor, key, delta, now)
                else if (delta > 0) CoinLedger.credit(connection, actor, key, delta, now)
            }
        }
        next.round?.let { game ->
            requireNotNull(next.pool).allocations(game).filter { allocation -> game.players.any { it.id == allocation.playerId && !it.computer } }
                .forEach { CoinLedger.credit(connection, it.playerId, "bingo:${game.id}:${it.key}", it.coins, now) }
        }
        save(connection, next)
        return next
    }
    private fun save(connection: Connection, room: BingoRoomRecord) {
        connection.execute("UPDATE bingo_rooms SET phase = ?, expires_at = ?, starts_at = ?, payload = ? WHERE id = ?",
            room.phase.name, room.expiresAt, room.startsAt, WireJson.encodeToString(room), room.id)
        room.members.forEach { connection.execute("INSERT INTO bingo_participants SELECT ?, id FROM guests WHERE id = ? ON CONFLICT DO NOTHING", room.id, it.id) }
    }
    private fun view(connection: Connection, room: BingoRoomRecord, actor: String) = room.view(actor, clock()).copy(wallet = CoinLedger.view(connection, actor))
    private fun load(connection: Connection, code: String): BingoRoomRecord {
        demand(code.matches(Regex("B-[A-Z2-9]{8}")), 404, "bingo_missing", "Bingo table not found.")
        val room = connection.query("SELECT payload FROM bingo_rooms WHERE code = ? FOR UPDATE", code) { decode(it.getString(1)) }.singleOrNull()
            ?: fail(404, "bingo_missing", "Bingo table not found.")
        demand(room.phase != RoomPhase.CLOSED && room.expiresAt > clock(), 410, "bingo_closed", "This Bingo table has closed.")
        return room
    }
    private fun member(room: BingoRoomRecord, actor: String) = demand(room.members.any { it.id == actor }, 403, "not_member", "Join this Bingo table before viewing it.")
    private fun decode(payload: String) = WireJson.decodeFromString<BingoRoomRecord>(payload)
    private fun validId(id: String) = demand(runCatching { UUID.fromString(id).toString() == id }.getOrDefault(false), 400, "invalid_id", "Use a new command identifier.")
    private fun receipt(connection: Connection, actor: String, id: String, hash: String): BingoRoomView? = connection.query(
        "SELECT request_hash, response FROM bingo_receipts WHERE actor = ? AND command_id = ?", actor, id) { Receipt(it.getString(1), it.getString(2)) }.singleOrNull()?.let {
        demand(it.hash == hash, 409, "id_reused", "This operation identifier was already used for another request.")
        WireJson.decodeFromString<BingoRoomView>(it.response)
    }
    private fun recordReceipt(connection: Connection, actor: String, id: String, hash: String, view: BingoRoomView) {
        connection.execute("INSERT INTO bingo_receipts VALUES (?, ?, ?, ?, ?)", actor, id, hash, view.roomId, WireJson.encodeToString(view))
    }
    private fun hasTambolaRoom(connection: Connection, actor: String, now: Long) = connection.query("""SELECT 1 FROM rooms
        WHERE phase IN ('LOBBY','ACTIVE') AND expires_at > ? AND id IN (SELECT room_id FROM room_participants WHERE player_id = ?)
        AND EXISTS (SELECT 1 FROM jsonb_array_elements(payload::jsonb->'members') m WHERE m->>'id' = ?) LIMIT 1""", now, actor, actor) { true }.isNotEmpty()

    fun hasActive(connection: Connection, actor: String, now: Long) = connection.query("""SELECT 1 FROM bingo_rooms
        WHERE phase IN ('LOBBY','ACTIVE') AND expires_at > ? AND id IN (SELECT room_id FROM bingo_participants WHERE player_id = ?)
        AND EXISTS (SELECT 1 FROM jsonb_array_elements(payload::jsonb->'members') m WHERE m->>'id' = ?) LIMIT 1""", now, actor, actor) { true }.isNotEmpty()

    /** Called by the same durable deletion replay as Tambola, before deleting the guest/wallet. */
    fun redact(connection: Connection, actor: String, now: Long) {
        connection.forEachRow("SELECT payload FROM bingo_rooms WHERE id IN (SELECT room_id FROM bingo_participants WHERE player_id = ?) ORDER BY id FOR UPDATE", actor) { row ->
            val before = decode(row.getString(1))
            val redacted = before.redact(actor)
            val members = redacted.members.filterNot { it.id == actor }
            val next = redacted.copy(members = members, purchases = if (before.phase == RoomPhase.LOBBY) before.purchases - actor else before.purchases,
                hostId = if (before.hostId == actor) members.firstOrNull()?.id.orEmpty() else before.hostId,
                phase = if (members.isEmpty()) RoomPhase.CLOSED else before.phase,
                round = if (members.isEmpty()) redacted.round?.cancel() else redacted.round,
                nextDrawAt = redacted.nextDrawAt.takeIf { members.isNotEmpty() }, startsAt = redacted.startsAt.takeIf { members.isNotEmpty() })
            changed(connection, before, next, now)
            connection.forEachRow("SELECT actor, command_id, response FROM bingo_receipts WHERE room_id = ? AND actor <> ? ORDER BY actor, command_id FOR UPDATE", before.id, actor) { receipt ->
                val view = WireJson.decodeFromString<BingoRoomView>(receipt.getString(3))
                fun players(list: List<Player>) = list.map { if (it.id == actor) it.copy(name = "Deleted player", avatar = 0) else it }
                val clean = view.copy(members = view.members.map { if (it.playerId == actor) it.copy(displayName = "Deleted player", avatar = 0) else it },
                    players = players(view.players), round = view.round?.let { it.copy(players = players(it.players)) })
                connection.execute("UPDATE bingo_receipts SET response = ? WHERE actor = ? AND command_id = ?", WireJson.encodeToString(clean), receipt.getString(1), receipt.getString(2))
            }
        }
    }
}
