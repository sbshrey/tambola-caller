package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.protocol.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import java.sql.DriverManager
import java.sql.SQLException
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Actual login roles and fresh databases: no SET ROLE simulation and no change to the shared test database ACL. */
class RuntimePrivilegesTest {
    private val suffix = UUID.randomUUID().toString().replace("-", "").take(16)
    private val mainName = "tambola_perm_main_$suffix"
    private val journalName = "tambola_perm_journal_$suffix"
    private val mainRole = "tambola_perm_rooms_$suffix"
    private val journalRole = "tambola_perm_delete_$suffix"
    private val groupRole = "tambola_perm_group_$suffix"
    private val mainPassword = secret()
    private val journalPassword = secret()
    private val roles = mutableSetOf<String>()
    private val databases = mutableSetOf<String>()
    private val pools = mutableListOf<Database>()
    private lateinit var url: String
    private lateinit var user: String
    private lateinit var password: String
    private lateinit var mainOwner: Database
    private lateinit var journalOwner: Database
    private lateinit var mainRuntime: Database
    private lateinit var journalRuntime: Database
    private lateinit var journal: DeletionJournal
    private lateinit var service: RoomService
    private val now = AtomicLong(System.currentTimeMillis())
    private fun target(name: String) = url.removeSuffix("tambola_test") + name
    private fun identifier(name: String): String { require(name.matches(Regex("[a-z][a-z0-9_]{0,62}"))); return "\"$name\"" }
    private fun admin(sql: String) = DriverManager.getConnection(url, user, password).use { it.createStatement().use { statement -> statement.execute(sql) } }
    private fun pool(name: String, role: String = user, credential: String = password) = Database(target(name), role, credential).also(pools::add)

    @Before fun prepare() {
        url = System.getenv("TAMBOLA_TEST_DATABASE_URL").orEmpty()
        require(url.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/tambola_test")))
        user = System.getenv("TAMBOLA_TEST_DATABASE_USER").orEmpty()
        password = System.getenv("TAMBOLA_TEST_DATABASE_PASSWORD").orEmpty()
        require(user.isNotBlank() && password.isNotBlank())
        listOf(mainRole to mainPassword, journalRole to journalPassword).forEach { (role, credential) ->
            require(credential.matches(Regex("[A-Za-z0-9_-]{43}")))
            admin("CREATE ROLE ${identifier(role)} LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS PASSWORD '$credential'")
            roles += role
        }
        admin("CREATE ROLE ${identifier(groupRole)} NOLOGIN"); roles += groupRole
        listOf(mainName, journalName).forEach { name -> admin("CREATE DATABASE ${identifier(name)} TEMPLATE template0"); databases += name }
        mainOwner = pool(mainName).also { it.migrate() }
        journalOwner = pool(journalName).also { DeletionJournal(it).migrate() }
        fun grant(db: Database, name: String, role: String, resource: String) {
            val sql = requireNotNull(javaClass.getResource("/db/permissions/$resource.sql")).readText()
                .replace(":\"database\"", identifier(name)).replace(":\"runtime_role\"", identifier(role))
            db.transaction { it.createStatement().use { statement -> statement.execute(sql) } }
        }
        grant(mainOwner, mainName, mainRole, "primary"); grant(journalOwner, journalName, journalRole, "journal")
        mainRuntime = pool(mainName, mainRole, mainPassword)
        journalRuntime = pool(journalName, journalRole, journalPassword)
        mainRuntime.verifyMigrations(); RuntimePrivileges.verify(mainRuntime, journal = false)
        journal = DeletionJournal(journalRuntime).also { it.verifyMigrations() }
        RuntimePrivileges.verify(journalRuntime, journal = true)
        service = RoomService(mainRuntime, now::get, journal)
        service.replayDeletions()
    }

    @After fun cleanup() {
        pools.forEach { it.close() }
        databases.toList().forEach { name ->
            check(name in listOf(mainName, journalName))
            admin("DROP DATABASE ${identifier(name)} WITH (FORCE)"); databases.remove(name)
        }
        roles.toList().forEach { role ->
            check(role in listOf(mainRole, journalRole, groupRole))
            admin("DROP ROLE ${identifier(role)}"); roles.remove(role)
        }
    }

    private fun denied(db: Database, sql: String, state: String = "42501") {
        val failure = assertThrows(SQLException::class.java) { db.transaction { it.execute(sql) } }
        assertEquals(state, failure.sqlState)
    }

    @Test fun `restricted roles play archive redact retry replay and clean retained records`() {
        val host = service.register(GuestRequest("Role host", 5), "host")
        val peer = service.register(GuestRequest("Role peer", 3), "peer")
        val code = service.create(host.token, CreateRoomRequest(UUID.randomUUID().toString(), RoomOptions(automaticCalling = false))).snapshot.code
        service.join(peer.token, code)
        fun command(player: GuestCredentials, action: RoomAction): RoomUpdate = service.command(player.token, code,
            CommandRequest(UUID.randomUUID().toString(), service.read(player.token, code).snapshot.revision, action))
        repeat(2) {
            command(host, RoomAction.Ready(true)); command(peer, RoomAction.Ready(true)); command(host, RoomAction.Start)
            command(host, RoomAction.Draw)
            if (it == 0) { command(host, RoomAction.End); command(host, RoomAction.Rematch) }
        }
        val before = service.read(peer.token, code).snapshot
        val deletion = DeleteProfileRequest(UUID.randomUUID().toString())
        val receipt = service.deleteProfile(host.token, deletion, "host")
        assertEquals(receipt, service.deleteProfile(host.token, deletion, "host"))
        assertEquals(1, service.replayDeletions())
        val after = service.read(peer.token, code).snapshot
        assertEquals(peer.playerId, after.hostId)
        assertEquals(before.round!!.ownTickets, after.round!!.ownTickets)
        assertEquals("Deleted player", after.round!!.players.first { it.id == host.playerId }.name)
        assertEquals(2, command(peer, RoomAction.Draw).snapshot.round!!.called.size)
        now.addAndGet(35 * ROOM_LIFETIME)
        service.cleanup()
        assertEquals(0, mainRuntime.transaction { it.query("SELECT count(*) FROM rooms") { row -> row.getInt(1) }.single() })
        assertTrue(journal.suppresses(host.playerId))
    }

    @Test fun `runtime cannot erase or rewrite suppression migration or journal identity`() {
        journal.append(UUID.randomUUID().toString(), digest("role-fixture"), now.get(), now.get() + ROOM_LIFETIME)
        listOf("DELETE FROM profile_deletions", "TRUNCATE profile_deletions", "UPDATE profile_deletions SET confirmation_hash = repeat('a',64)",
            "UPDATE deletion_journal_identity SET journal_id = 'changed'", "DELETE FROM deletion_journal_identity",
            "UPDATE journal_migrations SET checksum = 'changed'", "ALTER TABLE profile_deletions ADD COLUMN bypass text",
            "ALTER TABLE deletion_journal_identity DISABLE TRIGGER deletion_journal_head_guard", "DROP TABLE profile_deletions").forEach { denied(journalRuntime, it) }
        denied(journalRuntime, "UPDATE deletion_journal_identity SET head = 0", "23514")
        denied(journalRuntime, "UPDATE deletion_journal_identity SET head = 3", "23514")
        denied(journalRuntime, "UPDATE deletion_journal_identity SET head = 2", "23514")
        assertEquals(1L, journal.position().head)
    }

    @Test fun `runtime has no schema temp registry or permission delegation authority`() {
        listOf("CREATE TABLE forbidden (id int)", "CREATE TEMP TABLE guests (id int)", "CREATE SCHEMA forbidden",
            "UPDATE schema_migrations SET checksum = 'changed'", "DELETE FROM schema_migrations", "TRUNCATE guests",
            "ALTER TABLE guests ADD COLUMN bypass text", "INSERT INTO deletion_recovery VALUES(false, NULL, 0)").forEach { denied(mainRuntime, it) }
        assertThrows(IllegalStateException::class.java) { RuntimePrivileges.verify(mainOwner, journal = false) }
        assertThrows(SQLException::class.java) { mainRuntime.migrate() }
    }

    @Test fun `startup detects column grants delegation membership and missing required privileges`() {
        journalOwner.transaction { it.execute("GRANT UPDATE (player_id) ON profile_deletions TO ${identifier(journalRole)}") }
        assertThrows(IllegalStateException::class.java) { RuntimePrivileges.verify(journalRuntime, journal = true) }
        journalOwner.transaction { it.execute("REVOKE UPDATE (player_id) ON profile_deletions FROM ${identifier(journalRole)}") }
        mainOwner.transaction { it.execute("GRANT SELECT ON rooms TO ${identifier(mainRole)} WITH GRANT OPTION") }
        assertThrows(IllegalStateException::class.java) { RuntimePrivileges.verify(mainRuntime, journal = false) }
        mainOwner.transaction { it.execute("REVOKE GRANT OPTION FOR SELECT ON rooms FROM ${identifier(mainRole)}") }
        mainOwner.transaction { it.execute("GRANT UPDATE (response) ON command_receipts TO ${identifier(mainRole)} WITH GRANT OPTION") }
        assertThrows(IllegalStateException::class.java) { RuntimePrivileges.verify(mainRuntime, journal = false) }
        mainOwner.transaction { it.execute("REVOKE GRANT OPTION FOR UPDATE (response) ON command_receipts FROM ${identifier(mainRole)}") }
        admin("GRANT ${identifier(groupRole)} TO ${identifier(mainRole)}")
        assertThrows(IllegalStateException::class.java) { RuntimePrivileges.verify(mainRuntime, journal = false) }
        admin("REVOKE ${identifier(groupRole)} FROM ${identifier(mainRole)}")
        mainOwner.transaction { it.execute("REVOKE INSERT ON guests FROM ${identifier(mainRole)}") }
        assertThrows(IllegalStateException::class.java) { RuntimePrivileges.verify(mainRuntime, journal = false) }
    }

    @Test fun `verification rejects missing modified and newer migrations without repairing them`() {
        mainOwner.transaction { it.execute("DELETE FROM schema_migrations WHERE version = 3") }
        assertThrows(IllegalStateException::class.java) { mainRuntime.verifyMigrations() }
        assertEquals(2, mainOwner.transaction { it.query("SELECT count(*) FROM schema_migrations") { row -> row.getInt(1) }.single() })
        mainOwner.transaction { it.execute("INSERT INTO schema_migrations VALUES (3, ?)", digest(requireNotNull(javaClass.getResource("/db/003_deletion_recovery.sql")).readText())) }
        mainRuntime.verifyMigrations()
        mainOwner.transaction { it.execute("INSERT INTO schema_migrations VALUES(99, 'future')") }
        assertThrows(IllegalStateException::class.java) { mainRuntime.verifyMigrations() }
        assertThrows(IllegalStateException::class.java) { mainOwner.migrate() }
        mainOwner.transaction { it.execute("DELETE FROM schema_migrations WHERE version=99"); it.execute("UPDATE schema_migrations SET checksum='changed' WHERE version=2") }
        assertThrows(IllegalStateException::class.java) { mainRuntime.verifyMigrations() }
    }

    @Test fun `journal migration preserves existing intents identity and sequence`() {
        val before = journal.append(UUID.randomUUID().toString(), digest("existing"), now.get(), now.get() + ROOM_LIFETIME)
        val identity = journal.position()
        journalOwner.transaction {
            it.execute("DROP TRIGGER deletion_journal_head_guard ON deletion_journal_identity")
            it.execute("DROP FUNCTION guard_deletion_journal_head()")
            it.execute("DELETE FROM journal_migrations WHERE version=2")
        }
        assertThrows(IllegalStateException::class.java) { journal.verifyMigrations() }
        DeletionJournal(journalOwner).apply { migrate(); migrate() }
        journal.verifyMigrations()
        assertEquals(identity, journal.position()); assertEquals(before, journal.find(before.proof))
        assertEquals(2L, journal.append(UUID.randomUUID().toString(), digest("next"), now.get(), now.get() + ROOM_LIFETIME).sequence)
    }

    @Test fun `database connect privileges separate the primary and journal identities`() {
        val mainToJournal = assertThrows(SQLException::class.java) { DriverManager.getConnection(target(journalName), mainRole, mainPassword).close() }
        val journalToMain = assertThrows(SQLException::class.java) { DriverManager.getConnection(target(mainName), journalRole, journalPassword).close() }
        assertEquals("42501", mainToJournal.sqlState); assertEquals("42501", journalToMain.sqlState)
    }
}
