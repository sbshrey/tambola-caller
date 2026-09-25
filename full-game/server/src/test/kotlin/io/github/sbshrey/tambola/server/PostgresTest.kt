package io.github.sbshrey.tambola.server

import org.junit.After
import org.junit.Before
import java.sql.DriverManager
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong

/** Every test uses a new schema in an explicitly named test database. Never falls back to production. */
abstract class PostgresTest {
    protected lateinit var database: Database
    protected lateinit var service: RoomService
    protected val now = AtomicLong(1_790_000_000_000)
    private lateinit var schema: String
    private lateinit var url: String
    private lateinit var user: String
    private lateinit var password: String

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
        if (::schema.isInitialized) DriverManager.getConnection(url, user, password).use {
            check(schema.matches(Regex("test_[a-f0-9]{32}")))
            it.execute("DROP SCHEMA $schema CASCADE")
        }
    }
}
