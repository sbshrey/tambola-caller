package io.github.sbshrey.tambola.server

import io.ktor.server.application.*
import io.ktor.server.application.hooks.*
import io.ktor.server.request.*
import io.ktor.util.AttributeKey
import kotlinx.coroutines.CancellationException
import java.lang.management.ManagementFactory
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

internal enum class MetricRoute { LIVE, READY, GUEST, LOGOUT, DELETE, CREATE, JOIN, READ, COMMAND, OTHER }
internal enum class WorkerStep { REPLAY, TICK, CLEANUP }

/** Fixed-label, process-local counters. No request values, identities, SQL or exception messages are retained. */
class ServiceOperations(
    private val workerEnabled: Boolean,
    metricsToken: String? = null,
    private val clock: () -> Long = System::nanoTime,
) {
    init { require(metricsToken == null || metricsToken.matches(Regex("[A-Za-z0-9_-]{43,128}"))) { "Metrics token must be 43-128 URL-safe random characters" } }
    private val tokenHash = metricsToken?.let { digest(it).toByteArray(Charsets.US_ASCII) }
    private val started = clock()
    private val requests = Array(MetricRoute.entries.size) { Array(6) { Histogram() } }
    private val steps = Array(WorkerStep.entries.size) { Array(2) { Histogram() } }
    private val activeStreams = AtomicLong()
    private val streamFailures = AtomicLong()
    private val streamsOpened = AtomicLong()
    private val replayed = AtomicLong()
    @Volatile private var successfulPass: Long? = null
    @Volatile private var successfulCleanup: Long? = null
    @Volatile private var failed = false

    internal fun hasMetrics() = tokenHash != null
    internal fun authorizes(header: String?): Boolean = tokenHash != null && header?.startsWith("Bearer ") == true &&
        MessageDigest.isEqual(tokenHash, digest(header.removePrefix("Bearer ")).toByteArray(Charsets.US_ASCII))
    internal fun now() = clock()
    internal fun observe(route: MetricRoute, status: Int, start: Long) = requests[route.ordinal][(status / 100).takeIf { it in 1..5 } ?: 0].record(clock() - start)
    internal fun streamOpened() { activeStreams.incrementAndGet(); streamsOpened.incrementAndGet() }
    internal fun streamClosed() { activeStreams.decrementAndGet() }
    internal fun streamFailed() { streamFailures.incrementAndGet() }
    internal fun replayed(count: Int) { replayed.addAndGet(count.toLong()) }
    internal fun workerSucceeded() { successfulPass = clock(); failed = false }
    internal fun workerFailed() { failed = true }
    internal fun cleanupDue() = successfulCleanup?.let { clock() - it >= 60_000_000_000L } ?: true
    internal fun workerReady() = !workerEnabled || (!failed && successfulPass?.let { clock() - it <= 30_000_000_000L } == true)
    internal fun <T> step(step: WorkerStep, block: () -> T): T {
        val start = clock()
        try {
            val result = block()
            steps[step.ordinal][0].record(clock() - start)
            if (step == WorkerStep.CLEANUP) successfulCleanup = clock()
            return result
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { steps[step.ordinal][1].record(clock() - start); throw error }
    }

    internal fun render(primary: PoolStats, journal: PoolStats?): String = buildString {
        fun gauge(name: String, help: String, value: Number) { append("# HELP $name $help\n# TYPE $name gauge\n$name $value\n") }
        fun counter(name: String, help: String, value: Number) { append("# HELP $name $help\n# TYPE $name counter\n$name $value\n") }
        gauge("tambola_process_uptime_seconds", "Monotonic time since process monitoring started.", (clock() - started) / 1e9)
        gauge("tambola_worker_enabled", "Whether this process runs the room worker.", if (workerEnabled) 1 else 0)
        gauge("tambola_worker_ready", "Successful worker pass within 30 seconds and no later failure.", if (workerReady()) 1 else 0)
        gauge("tambola_worker_success_age_seconds", "Time since complete worker pass, or -1 before the first pass.", successfulPass?.let { (clock() - it) / 1e9 } ?: -1)
        gauge("tambola_cleanup_success_age_seconds", "Time since successful retention cleanup, or -1 before the first cleanup.", successfulCleanup?.let { (clock() - it) / 1e9 } ?: -1)
        gauge("tambola_websocket_active", "Currently open room event sessions, including authentication in progress.", activeStreams.get())
        counter("tambola_websocket_opened_total", "Room event sessions opened.", streamsOpened.get())
        counter("tambola_websocket_failures_total", "Sessions ended by a handled authorization or database failure.", streamFailures.get())
        counter("tambola_deletion_replayed_total", "Deletion intents applied by this process worker.", replayed.get())
        gauge("tambola_jvm_heap_used_bytes", "Current used JVM heap, not retained heap after collection.", ManagementFactory.getMemoryMXBean().heapMemoryUsage.used)
        gauge("tambola_jvm_heap_max_bytes", "JVM maximum heap.", ManagementFactory.getMemoryMXBean().heapMemoryUsage.max)
        gauge("tambola_jvm_threads", "Current live JVM threads.", ManagementFactory.getThreadMXBean().threadCount)
        (ManagementFactory.getOperatingSystemMXBean() as? com.sun.management.OperatingSystemMXBean)?.let {
            counter("tambola_process_cpu_seconds_total", "CPU time consumed by this process.", it.processCpuTime.coerceAtLeast(0) / 1e9)
        }
        append("# HELP tambola_database_connections Current pool connections by state; no database queries are run for a scrape.\n# TYPE tambola_database_connections gauge\n")
        listOfNotNull("primary" to primary, journal?.let { "journal" to it }).forEach { (store, pool) ->
            mapOf("active" to pool.active, "idle" to pool.idle, "pending" to pool.pending, "total" to pool.total).forEach { (state, count) ->
                append("tambola_database_connections{store=\"$store\",state=\"$state\"} $count\n")
            }
        }
        append("# HELP tambola_http_duration_seconds Completed HTTP response duration; excludes metrics and WebSocket sessions.\n# TYPE tambola_http_duration_seconds histogram\n")
        MetricRoute.entries.forEach { route -> requests[route.ordinal].forEachIndexed { status, histogram ->
            append(histogram.render("tambola_http_duration_seconds", "route=\"${route.name.lowercase()}\",status=\"${if (status == 0) "other" else "${status}xx"}\""))
        } }
        append("# HELP tambola_worker_step_duration_seconds Completed worker steps by fixed operation and outcome.\n# TYPE tambola_worker_step_duration_seconds histogram\n")
        WorkerStep.entries.forEach { step -> steps[step.ordinal].forEachIndexed { outcome, histogram ->
            append(histogram.render("tambola_worker_step_duration_seconds", "step=\"${step.name.lowercase()}\",outcome=\"${if (outcome == 0) "success" else "failure"}\""))
        } }
    }

    private class Histogram {
        private val bounds = doubleArrayOf(.05, .1, .25, .5, 1.0, 2.0, 5.0, 10.0)
        private val buckets = LongArray(bounds.size)
        private var count = 0L
        private var sum = 0.0
        @Synchronized fun record(nanos: Long) {
            val seconds = nanos.coerceAtLeast(0) / 1e9
            count++; sum += seconds
            bounds.forEachIndexed { index, upper -> if (seconds <= upper) buckets[index]++ }
        }
        @Synchronized fun render(name: String, labels: String) = buildString {
            bounds.forEachIndexed { index, upper -> append("${name}_bucket{$labels,le=\"$upper\"} ${buckets[index]}\n") }
            append("${name}_bucket{$labels,le=\"+Inf\"} $count\n${name}_sum{$labels} $sum\n${name}_count{$labels} $count\n")
        }
    }
}

/** A cleanup failure remains due, so the next pass retries instead of silently waiting another minute. */
internal class RoomWorker(private val service: RoomService, private val operations: ServiceOperations) {
    fun runPass() {
        try {
            operations.replayed(operations.step(WorkerStep.REPLAY) { service.replayDeletions(10) })
            operations.step(WorkerStep.TICK) { service.tick() }
            if (operations.cleanupDue()) operations.step(WorkerStep.CLEANUP) { service.cleanup() }
            operations.workerSucceeded()
        } catch (error: CancellationException) { throw error }
        catch (error: Exception) { operations.workerFailed(); throw error }
    }
}

internal class OperationsConfig { lateinit var operations: ServiceOperations }
private val requestStarted = AttributeKey<Long>("operations-request-started")
internal val OperationsPlugin = createApplicationPlugin("ServiceOperations", ::OperationsConfig) {
    val operations = pluginConfig.operations
    on(CallSetup) { it.attributes.put(requestStarted, operations.now()) }
    on(ResponseSent) { call ->
        val route = metricRoute(call.request.path())
        if (route != null) call.attributes.getOrNull(requestStarted)?.let { operations.observe(route, call.response.status()?.value ?: 500, it) }
    }
}

internal fun metricRoute(path: String): MetricRoute? = when (path) {
    "/internal/metrics" -> null
    "/health/live" -> MetricRoute.LIVE
    "/health/ready" -> MetricRoute.READY
    "/v1/guests" -> MetricRoute.GUEST
    "/v1/guests/me/logout" -> MetricRoute.LOGOUT
    "/v1/guests/me/delete" -> MetricRoute.DELETE
    "/v1/rooms" -> MetricRoute.CREATE
    else -> {
        val parts = path.split('/')
        if (parts.size in 4..5 && parts[1] == "v1" && parts[2] == "rooms" && parts[3].isNotEmpty()) {
            when (parts.getOrNull(4)) { null -> MetricRoute.READ; "join" -> MetricRoute.JOIN; "commands" -> MetricRoute.COMMAND; "events" -> null; else -> MetricRoute.OTHER }
        } else MetricRoute.OTHER
    }
}
