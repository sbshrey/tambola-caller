package io.github.sbshrey.tambola.server

import org.junit.After
import org.junit.Before
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.TimeUnit
import java.nio.file.Files
import java.nio.file.Path
import java.net.URI

/** Every test uses a new schema in an explicitly named test database. Never falls back to production. */
abstract class PostgresTest {
    protected lateinit var database: Database
    protected lateinit var service: RoomService
    protected val now = AtomicLong(1_790_000_000_000)
    private lateinit var schema: String
    private lateinit var url: String
    private lateinit var user: String
    private lateinit var password: String
    private val extraSchemas = mutableListOf<String>()
    private val extraDatabases = mutableListOf<Database>()

    protected fun additionalDatabase(): Database {
        val name = "test_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, user, password).use { it.execute("CREATE SCHEMA $name") }
        extraSchemas += name
        return Database(url, user, password, name).also { extraDatabases += it }
    }

    /** pg_dump/pg_restore operate only on this test's random primary schema; the journal schema stays live. */
    protected fun withPrimaryBackup(block: (restore: () -> Unit) -> Unit) {
        val uri = URI(url.removePrefix("jdbc:"))
        require(uri.host == "127.0.0.1" && uri.path == "/tambola_test")
        check(schema.matches(Regex("test_[a-f0-9]{32}")))
        val bin = Path.of(requireNotNull(System.getenv("TAMBOLA_PG_BIN")) { "Set TAMBOLA_PG_BIN to PostgreSQL 16 client binaries for the restore drill" })
        val suffix = if (System.getProperty("os.name").startsWith("Windows")) ".exe" else ""
        val backup = Files.createTempFile("tambola-test-backup-", ".dump")
        fun run(tool: String, vararg args: String) {
            val diagnostics = Files.createTempFile("tambola-test-tool-", ".log")
            val builder = ProcessBuilder(listOf(bin.resolve(tool + suffix).toString()) + args)
                .redirectOutput(ProcessBuilder.Redirect.DISCARD).redirectError(diagnostics.toFile())
            builder.environment().putAll(mapOf("PGHOST" to uri.host, "PGPORT" to uri.port.toString(), "PGDATABASE" to "tambola_test",
                "PGUSER" to user, "PGPASSWORD" to password, "PGCONNECT_TIMEOUT" to "5"))
            val process = builder.start()
            try {
                check(process.waitFor(30, TimeUnit.SECONDS)) { "$tool exceeded the test deadline" }
                check(process.exitValue() == 0) {
                    val first = Files.readAllLines(diagnostics).firstOrNull().orEmpty().replace(password, "[redacted]")
                        .replace(user, "test_role").replace(backup.toString(), "test_dump").replace(Regex("test_[a-f0-9]{32}"), "test_schema").take(240)
                    "$tool failed: $first; further tool output omitted"
                }
            } finally {
                if (process.isAlive) { process.destroyForcibly(); process.waitFor(5, TimeUnit.SECONDS) }
                Files.deleteIfExists(diagnostics)
            }
        }
        try {
            run("pg_dump", "--no-password", "--format=custom", "--strict-names", "--schema=$schema", "--no-owner", "--no-privileges", "--file=$backup")
            check(Files.size(backup) > 0)
            var restored = false
            block {
                check(!restored); restored = true
                database.close()
                DriverManager.getConnection(url, user, password).use {
                    // pg_restore --schema selects objects within the namespace, not its CREATE SCHEMA entry.
                    it.autoCommit = false
                    it.execute("DROP SCHEMA $schema CASCADE")
                    it.execute("CREATE SCHEMA $schema")
                    it.commit()
                }
                run("pg_restore", "--no-password", "--exit-on-error", "--no-owner", "--no-privileges", "--dbname=tambola_test", "--schema=$schema", backup.toString())
                database = Database(url, user, password, schema)
                database.migrate()
            }
            check(restored) { "The test must exercise restoration, not just create a dump" }
        } finally { Files.deleteIfExists(backup) }
    }

    @Before fun prepareDatabase() {
        url = System.getenv("TAMBOLA_DATABASE_URL").orEmpty()
        require(url.substringBefore('?').endsWith("/tambola_test")) { "Tests require TAMBOLA_TEST_DATABASE_URL ending in /tambola_test" }
        user = System.getenv("TAMBOLA_DATABASE_USER").orEmpty()
        password = System.getenv("TAMBOLA_DATABASE_PASSWORD").orEmpty()
        schema = "test_" + UUID.randomUUID().toString().replace("-", "")
        DriverManager.getConnection(url, user, password).use { it.execute("CREATE SCHEMA $schema") }
        database = Database(url, user, password, schema)
        database.migrate()
        service = RoomService(database, now::get)
    }

    @After fun disposeDatabase() {
        if (::database.isInitialized) database.close()
        extraDatabases.forEach { it.close() }
        if (::schema.isInitialized) DriverManager.getConnection(url, user, password).use {
            (listOf(schema) + extraSchemas).forEach { name ->
                check(name.matches(Regex("test_[a-f0-9]{32}")))
                it.execute("DROP SCHEMA IF EXISTS $name CASCADE")
            }
        }
    }
}
