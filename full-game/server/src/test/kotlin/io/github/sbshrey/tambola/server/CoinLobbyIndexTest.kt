package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class CoinLobbyIndexTest : PostgresTest() {
    private fun id() = UUID.randomUUID().toString()
    private fun guest() = service.register(GuestRequest("Index QA"), id())
    private fun buy(actor: GuestCredentials) = service.match(actor.token, MatchRequest(id(), 1)).snapshot
    private fun metadata(roomId: String) = database.transaction { connection ->
        connection.query("SELECT coin_starts_at, coin_human_seats FROM rooms WHERE id = ?", roomId) {
            (it.getObject(1) as Long?) to (it.getObject(2) as Int?)
        }.single()
    }

    @Test fun `full lobbies are skipped and a freed older seat becomes available again`() {
        val actors = List(8) { guest() }
        val full = actors.map(::buy).last()
        assertEquals(full.coins!!.startsAt to 8, metadata(full.roomId))
        now.incrementAndGet()
        val newer = buy(guest())
        assertNotEquals(full.roomId, newer.roomId)
        val current = service.read(actors.first().token, full.code).snapshot
        service.command(actors.first().token, full.code, CommandRequest(id(), current.revision, RoomAction.Leave))
        assertEquals(full.coins!!.startsAt to 7, metadata(full.roomId))
        val replacement = buy(guest())
        assertEquals(full.roomId, replacement.roomId)
        assertEquals(8, metadata(full.roomId).second)
        now.set(newer.coins!!.startsAt!!)
        service.tick()
        assertEquals(null to null, metadata(full.roomId))
        assertEquals(null to null, metadata(newer.roomId))
    }

    @Test fun `migration backfills existing lobbies and verifies idempotently`() {
        val lobby = buy(guest())
        database.transaction {
            it.execute("ALTER TABLE rooms DROP COLUMN coin_starts_at CASCADE, DROP COLUMN coin_human_seats CASCADE")
            it.execute("DELETE FROM schema_migrations WHERE version = 7")
        }
        database.migrate()
        assertEquals(lobby.coins!!.startsAt to 1, metadata(lobby.roomId))
        database.migrate(); database.verifyMigrations()
        assertEquals(lobby.roomId, buy(guest()).roomId)
    }

    @Test fun `payload only changes retain HOT eligibility unlike an expression index`() {
        database.transaction { connection ->
            // Small controlled storage fixture; no HTTP game runs against this diagnostic row.
            connection.execute("INSERT INTO rooms(id, code, phase, expires_at, payload, matchable) VALUES ('hot-qa', 'HOTTEST1', 'LOBBY', 1000000, ?, true)",
                """{"startsAt":100000,"members":[],"revision":0}""")
            fun revise(value: Int) { connection.execute("UPDATE rooms SET payload = jsonb_set(payload::jsonb, '{revision}', to_jsonb(?::int))::text WHERE id = 'hot-qa'", value) }
            fun counters() = connection.query("SELECT n_tup_upd, n_tup_hot_upd FROM pg_stat_xact_user_tables WHERE schemaname = current_schema() AND relname = 'rooms'") {
                it.getLong(1) to it.getLong(2)
            }.single()
            revise(1); revise(2)
            assertEquals(2L to 2L, counters())
            connection.execute("CREATE INDEX hot_expression_control ON rooms ((payload::jsonb->>'startsAt'))")
            revise(3); revise(4)
            assertEquals(4L to 2L, counters())
        }
    }

    @Test fun `a real backup restores generated lobby metadata and remains purchasable`() {
        val first = guest(); val second = guest()
        val lobby = buy(first)
        withPrimaryBackup { restore ->
            buy(second)
            assertEquals(2, metadata(lobby.roomId).second)
            restore()
            service = RoomService(database, now::get)
            assertEquals(lobby.coins!!.startsAt to 1, metadata(lobby.roomId))
            assertEquals(1500L, service.wallet(second.token).balance)
            assertEquals(lobby.roomId, buy(second).roomId)
            assertEquals(2, metadata(lobby.roomId).second)
            assertEquals(1400L, service.wallet(second.token).balance)
        }
    }
}
