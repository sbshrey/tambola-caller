package io.github.sbshrey.tambola.server

import java.sql.Connection

/** Ordered immutable resources; ordinary startup calls verify, which performs no DDL. */
internal class Migrations(private val registry: String, private val lock: Long, private val resources: List<String>) {
    private val expected get() = resources.mapIndexed { index, path ->
        index + 1 to requireNotNull(javaClass.getResource(path)).readText()
    }

    fun migrate(connection: Connection, afterInstall: (Int) -> Unit = {}) {
        connection.query("SELECT pg_advisory_xact_lock($lock)") { true }
        connection.execute("CREATE TABLE IF NOT EXISTS $registry (version integer PRIMARY KEY, checksum text NOT NULL)")
        val installed = installed(connection)
        check(installed.keys.all { it in 1..resources.size }) { "Database migration version is newer than this service" }
        expected.forEach { (version, sql) ->
            if (version !in installed) {
                connection.createStatement().use { it.execute(sql) }
                afterInstall(version)
                connection.execute("INSERT INTO $registry VALUES (?, ?)", version, digest(sql))
            } else check(installed[version] == digest(sql)) { "Installed migration checksum differs from source" }
        }
        verify(connection)
    }

    fun verify(connection: Connection) {
        val installed = installed(connection)
        check(installed == expected.associate { (version, sql) -> version to digest(sql) }) {
            "Database migrations do not match this service; run the matching migration command before serving"
        }
    }

    private fun installed(connection: Connection) = connection.query("SELECT version, checksum FROM $registry") {
        it.getInt(1) to it.getString(2)
    }.toMap()
}
