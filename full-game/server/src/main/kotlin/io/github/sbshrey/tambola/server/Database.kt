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

    fun <T> transaction(readOnly: Boolean = false, block: (Connection) -> T): T = pool.connection.use { connection ->
        connection.isReadOnly = readOnly
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

    private val migrations = Migrations("schema_migrations", 749023801,
        listOf("001_rooms.sql", "002_profile_deletion.sql", "003_deletion_recovery.sql", "004_coin_wallets.sql", "005_coin_matches.sql", "006_device_sessions.sql", "007_open_coin_lobbies.sql").map { "/db/$it" })
    fun migrate() = transaction { migrations.migrate(it) }
    fun verifyMigrations() = transaction { migrations.verify(it) }

    fun healthy(): Boolean = transaction { it.query("SELECT 1") { row -> row.getInt(1) }.single() == 1 }
    internal fun poolStats() = pool.hikariPoolMXBean.let { PoolStats(it.activeConnections, it.idleConnections, it.threadsAwaitingConnection, it.totalConnections) }
    override fun close() = pool.close()
}

internal data class PoolStats(val active: Int, val idle: Int, val pending: Int, val total: Int)

internal fun Connection.execute(sql: String, vararg params: Any?): Int = prepareStatement(sql).use { statement ->
    params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    statement.executeUpdate()
}

internal fun <T> Connection.query(sql: String, vararg params: Any?, map: (ResultSet) -> T): List<T> = prepareStatement(sql).use { statement ->
    params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
    statement.executeQuery().use { result -> buildList { while (result.next()) add(map(result)) } }
}

/** Fetch a bounded page of rows inside the caller's transaction, without collecting decoded history.
 * Updates must use another statement and must not change this query's ordering keys. */
internal fun Connection.forEachRow(sql: String, vararg params: Any?, consume: (ResultSet) -> Unit) {
    check(!autoCommit) { "Streaming rows requires a transaction" }
    prepareStatement(sql, ResultSet.TYPE_FORWARD_ONLY, ResultSet.CONCUR_READ_ONLY).use { statement ->
        statement.fetchSize = 32
        params.forEachIndexed { index, value -> statement.setObject(index + 1, value) }
        statement.executeQuery().use { rows -> while (rows.next()) consume(rows) }
    }
}
