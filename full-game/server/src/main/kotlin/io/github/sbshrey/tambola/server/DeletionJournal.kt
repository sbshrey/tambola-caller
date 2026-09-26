package io.github.sbshrey.tambola.server

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

data class DeletionIntent(val sequence: Long, val playerId: String, val proof: String, val deletedAt: Long, val confirmUntil: Long)
data class JournalPosition(val id: String, val head: Long)

/** A separate PostgreSQL database, never restored together with the room database. No raw tokens or names. */
class DeletionJournal(private val database: Database) {
    @Volatile private var expectedId: String? = null

    fun migrate() {
        database.transaction { connection ->
            connection.query("SELECT pg_advisory_xact_lock(749023802)") { true }
            connection.execute("CREATE TABLE IF NOT EXISTS journal_migrations (version integer PRIMARY KEY, checksum text NOT NULL)")
            val sql = requireNotNull(javaClass.getResource("/db/journal/001_deletions.sql")).readText()
            val checksum = digest(sql)
            val installed = connection.query("SELECT checksum FROM journal_migrations WHERE version = 1") { it.getString(1) }.singleOrNull()
            if (installed == null) {
                connection.createStatement().use { it.execute(sql) }
                connection.execute("INSERT INTO deletion_journal_identity(singleton, journal_id) VALUES (true, ?)", UUID.randomUUID().toString())
                connection.execute("INSERT INTO journal_migrations VALUES (1, ?)", checksum)
            } else check(installed == checksum) { "Installed journal migration differs from source" }
        }
        expectedId = position().id
    }

    private fun position(connection: Connection, lock: Boolean = false): JournalPosition {
        val position = connection.query("SELECT journal_id, head FROM deletion_journal_identity WHERE singleton${if (lock) " FOR UPDATE" else ""}") {
            JournalPosition(it.getString(1), it.getLong(2))
        }.single()
        recoveryCheck(expectedId == null || expectedId == position.id, "Deletion journal identity changed")
        return position
    }

    fun position(): JournalPosition = database.transaction { position(it) }
    fun healthy(): Boolean { position(); return true }

    /** Also catches differently spelled URLs pointing at the same database/schema. */
    fun verifyRestoreBoundary(primary: Database) {
        val primaryContainsJournal = primary.transaction { it.query("SELECT to_regclass('deletion_journal_identity') IS NOT NULL") { row -> row.getBoolean(1) }.single() }
        val journalContainsRooms = database.transaction { it.query("SELECT to_regclass('rooms') IS NOT NULL") { row -> row.getBoolean(1) }.single() }
        recoveryCheck(!primaryContainsJournal && !journalContainsRooms, "Room data and deletion journal must have separate restore boundaries")
    }

    /** The head-row lock makes sequence order also commit order. A rollback creates no sequence gap. */
    fun append(playerId: String, proof: String, deletedAt: Long, confirmUntil: Long): DeletionIntent = database.transaction { connection ->
        require(UUID.fromString(playerId).toString() == playerId && proof.matches(Regex("[0-9a-f]{64}")))
        require(confirmUntil > deletedAt)
        val head = position(connection, lock = true).head
        connection.query("SELECT * FROM profile_deletions WHERE player_id = ?", playerId, map = ::intent).singleOrNull()?.let {
            demand(it.proof == proof, 401, "unauthorized", "Use the original deletion request to confirm it.")
            return@transaction it
        }
        val result = DeletionIntent(Math.addExact(head, 1), playerId, proof, deletedAt, confirmUntil)
        connection.execute("INSERT INTO profile_deletions VALUES (?, ?, ?, ?, ?)", result.sequence, playerId, proof, deletedAt, confirmUntil)
        connection.execute("UPDATE deletion_journal_identity SET head = ? WHERE singleton", result.sequence)
        result
    }

    fun find(proof: String): DeletionIntent? = database.transaction { connection ->
        position(connection)
        connection.query("SELECT * FROM profile_deletions WHERE confirmation_hash = ?", proof, map = ::intent).singleOrNull()
    }

    fun suppresses(playerId: String): Boolean = database.transaction { connection ->
        position(connection)
        connection.query("SELECT 1 FROM profile_deletions WHERE player_id = ?", playerId) { true }.isNotEmpty()
    }

    fun next(after: Long, through: Long): DeletionIntent? = database.transaction { connection ->
        val current = position(connection)
        recoveryCheck(after <= current.head && through <= current.head, "Deletion recovery cursor exceeds journal head")
        val next = connection.query("SELECT * FROM profile_deletions WHERE sequence > ? AND sequence <= ? ORDER BY sequence LIMIT 1", after, through, map = ::intent).singleOrNull()
        recoveryCheck((next == null && after == through) || next?.sequence == after + 1, "Deletion journal has a sequence gap")
        next
    }

    private fun intent(row: ResultSet) = DeletionIntent(row.getLong("sequence"), row.getString("player_id"),
        row.getString("confirmation_hash"), row.getLong("deleted_at"), row.getLong("confirm_until"))
}

internal fun recoveryCheck(value: Boolean, message: String) {
    if (!value) throw SQLException(message, "55000")
}
