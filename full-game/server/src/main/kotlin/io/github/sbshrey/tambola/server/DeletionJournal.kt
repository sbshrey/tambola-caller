package io.github.sbshrey.tambola.server

import java.sql.Connection
import java.sql.ResultSet
import java.sql.SQLException
import java.util.UUID

sealed interface RecoveryIntent { val sequence: Long; val playerId: String }
data class DeletionIntent(override val sequence: Long, override val playerId: String, val proof: String, val deletedAt: Long, val confirmUntil: Long) : RecoveryIntent
data class RevocationIntent(override val sequence: Long, override val playerId: String, val revokedAt: Long) : RecoveryIntent
data class JournalPosition(val id: String, val head: Long)
internal data class JournalAccess(val position: JournalPosition, val blocked: Boolean)

/** A separate PostgreSQL database, never restored together with the room database. No raw tokens or names. */
class DeletionJournal(private val database: Database) {
    @Volatile private var expectedId: String? = null
    private val migrations = Migrations("journal_migrations", 749023802,
        listOf("001_deletions.sql", "002_head_guard.sql", "003_session_revocations.sql").map { "/db/journal/$it" })

    fun migrate() {
        database.transaction { connection ->
            migrations.migrate(connection) { version ->
                if (version == 1) connection.execute("INSERT INTO deletion_journal_identity(singleton, journal_id) VALUES (true, ?)", UUID.randomUUID().toString())
            }
        }
        expectedId = position().id
    }

    fun verifyMigrations() {
        database.transaction { migrations.verify(it) }
        expectedId = position().id
    }

    private fun position(connection: Connection, lock: Boolean = false): JournalPosition {
        val position = connection.query("SELECT journal_id, head FROM deletion_journal_identity WHERE singleton${if (lock) " FOR UPDATE" else ""}") {
            JournalPosition(it.getString(1), it.getLong(2))
        }.single()
        return checkedPosition(position)
    }

    private fun checkedPosition(position: JournalPosition): JournalPosition {
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

    /** Logout permanently revokes this profile's credentials without deleting its shared history. */
    fun revoke(playerId: String, revokedAt: Long): RevocationIntent = database.transaction { connection ->
        require(UUID.fromString(playerId).toString() == playerId)
        val head = position(connection, lock = true).head
        connection.query("SELECT sequence, player_id, revoked_at FROM session_revocations WHERE player_id = ?", playerId) {
            RevocationIntent(it.getLong(1), it.getString(2), it.getLong(3))
        }.singleOrNull()?.let { return@transaction it }
        val result = RevocationIntent(Math.addExact(head, 1), playerId, revokedAt)
        connection.execute("INSERT INTO session_revocations VALUES (?, ?, ?)", result.sequence, playerId, revokedAt)
        connection.execute("UPDATE deletion_journal_identity SET head = ? WHERE singleton", result.sequence)
        result
    }

    /** One fresh statement observes journal identity, head and suppression at the same snapshot.
     * A null player still verifies availability/identity for an unavailable primary credential. */
    internal fun accessState(playerId: String?): JournalAccess = database.transaction(readOnly = true) { connection ->
        connection.query("""SELECT journal_id, head,
            EXISTS (SELECT 1 FROM profile_deletions WHERE player_id = ?)
            OR EXISTS (SELECT 1 FROM session_revocations WHERE player_id = ?)
            FROM deletion_journal_identity WHERE singleton""", playerId, playerId) {
            JournalAccess(checkedPosition(JournalPosition(it.getString(1), it.getLong(2))), it.getBoolean(3))
        }.single()
    }

    fun blocksAccess(playerId: String): Boolean = accessState(playerId).blocked

    fun suppresses(playerId: String): Boolean = database.transaction { connection ->
        position(connection)
        connection.query("SELECT 1 FROM profile_deletions WHERE player_id = ?", playerId) { true }.isNotEmpty()
    }

    fun next(after: Long, through: Long): RecoveryIntent? = database.transaction { connection ->
        val current = position(connection)
        recoveryCheck(after <= current.head && through <= current.head, "Deletion recovery cursor exceeds journal head")
        val entries = connection.query("""SELECT sequence, player_id, confirmation_hash, deleted_at AS occurred_at, confirm_until
            FROM profile_deletions WHERE sequence > ? AND sequence <= ?
            UNION ALL SELECT sequence, player_id, NULL, revoked_at, NULL
            FROM session_revocations WHERE sequence > ? AND sequence <= ? ORDER BY sequence LIMIT 2""", after, through, after, through) {
            val proof = it.getString("confirmation_hash")
            if (proof == null) RevocationIntent(it.getLong("sequence"), it.getString("player_id"), it.getLong("occurred_at"))
            else DeletionIntent(it.getLong("sequence"), it.getString("player_id"), proof, it.getLong("occurred_at"), it.getLong("confirm_until"))
        }
        recoveryCheck(entries.map { it.sequence }.distinct().size == entries.size, "Recovery journal has a duplicate sequence")
        val next = entries.firstOrNull()
        recoveryCheck((next == null && after == through) || next?.sequence == after + 1, "Deletion journal has a sequence gap")
        next
    }

    private fun intent(row: ResultSet) = DeletionIntent(row.getLong("sequence"), row.getString("player_id"),
        row.getString("confirmation_hash"), row.getLong("deleted_at"), row.getLong("confirm_until"))
}

internal fun recoveryCheck(value: Boolean, message: String) {
    if (!value) throw SQLException(message, "55000")
}
