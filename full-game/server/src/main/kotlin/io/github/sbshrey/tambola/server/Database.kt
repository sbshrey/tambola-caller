package io.github.sbshrey.tambola.server

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import java.sql.Connection
import java.sql.ResultSet

class Database(url: String, user: String, password: String, schema: String = "public") : AutoCloseable {
    init {
        require(url.startsWith("jdbc:postgresql://")) { "A PostgreSQL JDBC URL is required" }
        require(user.isNotBlank() && password.isNotBlank()) { "Database credentials are required" }
        require(schema.matches(Regex("[a-z][a-z0-9_]{0,62}")))
    }
    private val pool = HikariDataSource(HikariConfig().apply {
        jdbcUrl = url; username = user; this.password = password
        maximumPoolSize = 8; minimumIdle = 1; connectionTimeout = 5_000
        connectionInitSql = "SET search_path TO $schema"
        addDataSourceProperty("ApplicationName", "tambola-rooms")
        addDataSourceProperty("connectTimeout", "5")
        addDataSourceProperty("socketTimeout", "15")
    })

    fun <T> transaction(block: (Connection) -> T): T = pool.connection.use { connection ->
        connection.autoCommit = false
        try {
            connection.execute("SET LOCAL lock_timeout = '5s'")
            connection.execute("SET LOCAL statement_timeout = '10s'")
            val result = block(connection)
            connection.commit()
            result
        } catch (error: Throwable) {
            connection.rollback()
            throw error
        }
    }

    fun migrate() = transaction { connection ->
        connection.query("SELECT pg_advisory_xact_lock(749023801)") { true }
        connection.execute("CREATE TABLE IF NOT EXISTS schema_migrations (version integer PRIMARY KEY, checksum text NOT NULL)")
        listOf("001_rooms.sql", "002_profile_deletion.sql", "003_deletion_recovery.sql").forEachIndexed { index, file ->
            val version = index + 1
            val sql = requireNotNull(javaClass.getResource("/db/$file")).readText()
            val checksum = digest(sql)
            val installed = connection.query("SELECT checksum FROM schema_migrations WHERE version = ?", version) { it.getString(1) }.singleOrNull()
            if (installed == null) {
                connection.createStatement().use { it.execute(sql) }
                connection.execute("INSERT INTO schema_migrations VALUES (?, ?)", version, checksum)
            } else check(installed == checksum) { "Installed migration checksum differs from source" }
        }
    }

    fun healthy(): Boolean = transaction { it.query("SELECT 1") { row -> row.getInt(1) }.single() == 1 }
    override fun close() = pool.close()
}

internal fun Connection.execute(sql: String, vararg params: Any?): Int = prepareStatement(sql).use { statement ->
    params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    statement.executeUpdate()
}

internal fun <T> Connection.query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> = prepareStatement(sql).use { statement ->
    params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    statement.executeQuery().use { result -> buildList { while (result.next()) add(map(result)) } }
}
