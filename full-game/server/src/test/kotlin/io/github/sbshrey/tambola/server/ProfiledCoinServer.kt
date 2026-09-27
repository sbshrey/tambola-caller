package io.github.sbshrey.tambola.server

import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.lang.reflect.Proxy
import java.sql.Connection
import java.sql.Driver
import java.sql.DriverManager
import java.sql.PreparedStatement
import java.util.Properties
import java.time.Duration
import jdk.jfr.consumer.RecordingStream

/** Diagnostic launcher only: fixed timing labels, no query text, bindings, identities or credentials. */
object ProfiledCoinServer {
    private val originNanos = System.nanoTime()
    @JvmStatic fun main(args: Array<String>) {
        require(args.isEmpty() && System.getenv("TAMBOLA_COIN_LOAD_SQL_PROFILE") == "true")
        val allocationWindows = System.getenv("TAMBOLA_COIN_LOAD_ALLOCATION_WINDOWS") == "true"
        Class.forName("org.postgresql.Driver")
        val original = DriverManager.getDrivers().toList().single { it.javaClass.name == "org.postgresql.Driver" }
        DriverManager.deregisterDriver(original)
        DriverManager.registerDriver(object : Driver by original {
            override fun connect(url: String?, info: Properties?): Connection? = original.connect(url, info)?.let { instrument(it, allocationWindows) }
        })
        val poolProfile = System.getenv("TAMBOLA_COIN_LOAD_POOL_PROFILE") == "true"
        val requestProfile = System.getenv("TAMBOLA_COIN_LOAD_REQUEST_PROFILE") == "true"
        if (poolProfile || requestProfile) {
            val recording = RecordingStream()
            if (requestProfile) {
                recording.enable("tambola.PurchaseTiming").withThreshold(Duration.ZERO).withoutStackTrace()
                recording.onEvent("tambola.PurchaseTiming") { event ->
                    val fields = listOf("admissionNanos", "dispatchNanos", "serviceNanos", "poolNanos", "allocationNanos", "remainderNanos", "totalNanos")
                    println("COIN_REQUEST_TIMING|${event.startTime.toEpochMilli()}|${event.getBoolean("friendTable")}|${event.getBoolean("success")}|${event.getInt("poolAcquisitions")}|" +
                        fields.joinToString("|") { event.getLong(it).toString() })
                }
            }
            if (poolProfile) {
                recording.enable("jdk.ThreadPark").withThreshold(Duration.ofMillis(1)).withStackTrace()
                recording.onEvent("jdk.ThreadPark") { event ->
                    val frames = event.stackTrace?.frames.orEmpty()
                    if (frames.any { it.method.type.name == "io.github.sbshrey.tambola.server.RoomService" && it.method.name == "match" } &&
                        frames.any { it.method.type.name == "com.zaxxer.hikari.util.ConcurrentBag" && it.method.name == "borrow" }) {
                        val rate = frames.any { it.method.name == "authenticatedRate" }
                        val journal = frames.any { it.method.type.name.startsWith("io.github.sbshrey.tambola.server.DeletionJournal") }
                        val label = (if (rate) "rate" else "match") + (if (journal) "_journal" else "_primary")
                        println("COIN_POOL_PARK|$label|${event.startTime.toEpochMilli()}|${event.duration.toNanos()}")
                    }
                }
                recording.onFlush { println("COIN_POOL_FLUSH|${System.currentTimeMillis()}") }
            }
            recording.startAsync()
        }
        io.github.sbshrey.tambola.server.main(args)
    }

    private class Sample(val wait: Long, val started: Long, val allocationWindows: Boolean, val emit: (String) -> Unit) {
        private val startedEpochMs = if (allocationWindows) System.currentTimeMillis() else 0L
        val queries = linkedMapOf<String, LongArray>()
        fun record(label: String, nanos: Long) {
            val item = queries.getOrPut(label) { longArrayOf(0, 0) }
            item[0]++; item[1] += nanos
        }
        fun finish(commit: Long) {
            val ended = System.nanoTime()
            val endedEpochMs = if (allocationWindows) System.currentTimeMillis() else 0L
            val duration = ended - started
            val items = queries.entries.joinToString(";") { (label, values) ->
                "$label:${values[0]}:${values[1]}"
            }
            emit("COIN_SQL_TIMING|$wait|$duration|$commit|$items")
            if (allocationWindows) emit("COIN_ALLOCATION_WINDOW|$startedEpochMs|$endedEpochMs|${started - originNanos}|${ended - originNanos}|$wait|$commit|$items")
        }
    }

    private fun invoke(target: Any, method: Method, args: Array<out Any?>?): Any? = try {
        method.invoke(target, *(args ?: emptyArray()))
    } catch (error: InvocationTargetException) { throw error.cause ?: error }

    internal fun instrument(connection: Connection, allocationWindows: Boolean = false, emit: (String) -> Unit = ::println): Connection {
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
                                if (allocationLock) sample = Sample(elapsed, System.nanoTime(), allocationWindows, emit)
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
