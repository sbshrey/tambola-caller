package io.github.sbshrey.tambola.server

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Properties

/** Diagnostic launcher only: fixed timing labels, no query text, bindings, identities or credentials. */
object ProfiledCoinServer {
    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty() && System.getenv("TAMBOLA_COIN_LOAD_SQL_PROFILE") == "true")
        Class.forName("org.postgresql.Driver")
        val original = DriverManager.getDrivers().toList().single { it.javaClass.name == "org.postgresql.Driver" }
        DriverManager.deregisterDriver(original)
        DriverManager.registerDriver(object : Driver by original {
            override fun connect(url: String?, info: Properties?): Connection? = original.connect(url, info)?.let(::instrument)
        })
        io.github.sbshrey.tambola.server.main(args)
    }

    private class Sample(val wait: Long, val started: Long) {
        val queries = linkedMapOf<String, LongArray>()
        fun record(label: String, nanos: Long) {
            val item = queries.getOrPut(label) { longArrayOf(0, 0) }
            item[0]++; item[1] += nanos
        }
        fun finish(commit: Long) {
            val duration = System.nanoTime() - started
            println("COIN_SQL_TIMING|$wait|$duration|$commit|" + queries.entries.joinToString(";") { (label, values) ->
                "$label:${values[0]}:${values[1]}"
            })
        }
    }

    private fun invoke(target: Any, method: Method, args: Array<out Any?>?): Any? = try {
        method.invoke(target, *(args ?: emptyArray()))
    } catch (error: InvocationTargetException) { throw error.cause ?: error }

    private fun instrument(connection: Connection): Connection {
        var sample: Sample? = null
        return Proxy.newProxyInstance(Connection::class.java.classLoader, arrayOf(Connection::class.java)) { _, method, args ->
            when (method.name) {
                "prepareStatement" -> {
                    val statement = invoke(connection, method, args) as PreparedStatement
                    val sql = args!![0] as String
                    val allocationLock = sql == "SELECT pg_advisory_xact_lock(749023809)"
                    val label = category(sql)
                    Proxy.newProxyInstance(PreparedStatement::class.java.classLoader, arrayOf(PreparedStatement::class.java)) { _, call, values ->
                        if (call.name !in setOf("executeQuery", "executeUpdate", "execute", "executeBatch")) invoke(statement, call, values)
                        else {
                            val start = System.nanoTime()
                            invoke(statement, call, values).also {
                                val elapsed = System.nanoTime() - start
                                if (allocationLock) sample = Sample(elapsed, System.nanoTime())
                                else sample?.record(label, elapsed)
                            }
                        }
                    }
                }
                "commit" -> {
                    val start = System.nanoTime()
                    invoke(connection, method, args).also { sample?.finish(System.nanoTime() - start); sample = null }
                }
                "rollback", "close" -> { sample = null; invoke(connection, method, args) }
                else -> invoke(connection, method, args)
            }
        } as Connection
    }

    private fun category(sql: String): String = when {
        sql.contains("FOR NO KEY UPDATE") -> "wallet_lock"
        sql.startsWith("SELECT amount FROM coin_ledger") -> "ledger_previous"
        sql.contains("FROM coin_wallets w LEFT JOIN coin_ledger") -> "wallet_view"
        sql.startsWith("INSERT INTO coin_ledger") -> "ledger_insert"
        sql.startsWith("SELECT payload FROM rooms") -> "room_select"
        sql.startsWith("INSERT INTO rooms") -> "room_insert"
        sql.startsWith("UPDATE rooms") -> "room_update"
        sql.startsWith("INSERT INTO room_participants") -> "participants_insert"
        sql.startsWith("INSERT INTO room_events") -> "event_insert"
        sql.startsWith("INSERT INTO match_receipts") -> "receipt_insert"
        else -> "other"
    }
}
