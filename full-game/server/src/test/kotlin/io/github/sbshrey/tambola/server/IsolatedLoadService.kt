package io.github.sbshrey.tambola.server

import io.github.sbshrey.tambola.domain.AVATAR_COUNT
import io.github.sbshrey.tambola.protocol.GuestCredentials
import kotlinx.coroutines.delay
import java.net.InetAddress
import java.net.ServerSocket
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.nio.file.Files
import java.nio.file.Path
import java.sql.Connection
import java.sql.DriverManager
import java.time.Duration
import java.util.Properties
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Owns fresh primary/journal databases and one real server JVM. Never accepts a hosted database. */
internal class IsolatedLoadService : AutoCloseable {
    private fun required(key: String) = requireNotNull(System.getenv(key)?.takeIf(String::isNotBlank)) { "$key is required" }
    private val controlUrl = required("TAMBOLA_TEST_DATABASE_URL").also {
        require(it.matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/tambola_test")))
    }
    private val user = required("TAMBOLA_TEST_DATABASE_USER")
    private val password = required("TAMBOLA_TEST_DATABASE_PASSWORD")
    private val jdbc = Properties().apply {
        setProperty("user", user); setProperty("password", password)
        setProperty("connectTimeout", "5"); setProperty("socketTimeout", "30")
    }
    val root: Path = Path.of("").toAbsolutePath()
    val runId: String = UUID.randomUUID().toString().replace("-", "").take(16)
    val directory: Path = root.resolve(".test-workspace/coin-load-$runId").also(Files::createDirectories)
    private val suppliedRuntime = System.getenv("TAMBOLA_COIN_LOAD_RUNTIME_LIB")?.takeIf(String::isNotBlank)
    val runtimeSource = if (suppliedRuntime == null) "local-build" else "verified-copy"
    val runtimeLib: Path = if (suppliedRuntime == null) root.resolve("server/build/install/server/lib") else {
        val source = Path.of(suppliedRuntime).toRealPath()
        val expected = required("TAMBOLA_COIN_LOAD_RUNTIME_SHA256").also { require(it.matches(Regex("[a-f0-9]{64}"))) }
        val identity = serviceRuntimeIdentity(root, source)
        check(identity.getValue("serviceRuntimeSha256") == expected) { "Supplied runtime identity mismatch" }
        val target = directory.resolve("runtime-lib").also(Files::createDirectory)
        Files.list(source).use { paths -> paths.filter { it.fileName.toString().endsWith(".jar") }.forEach {
            Files.copy(it, target.resolve(it.fileName))
        } }
        check(serviceRuntimeIdentity(root, target) == identity) { "Copied runtime identity mismatch" }
        target
    }
    val runtimeIdentity = serviceRuntimeIdentity(root, runtimeLib)
    val sqlProfiling = System.getenv("TAMBOLA_COIN_LOAD_SQL_PROFILE") == "true"
    private val names = listOf("tambola_load_main_$runId", "tambola_load_journal_$runId")
    private val owned = mutableSetOf<String>()
    private val port = ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { it.localPort }
    val base = "http://127.0.0.1:$port"
    private var child: Process? = null
    val pid: Long? get() = child?.pid()
    private fun databaseUrl(index: Int) = controlUrl.removeSuffix("tambola_test") + names[index]
    private fun control(sql: String) = DriverManager.getConnection(controlUrl, jdbc).use { it.createStatement().use { s -> s.execute(sql) } }
    fun <T> primary(block: (Connection) -> T): T = DriverManager.getConnection(databaseUrl(0), jdbc).use(block)
    fun alive() = check(child?.isAlive == true) { "Owned server exited" }

    suspend fun start() {
        check(child == null && owned.isEmpty())
        DriverManager.getConnection(controlUrl, jdbc).use {
            check(it.query("SELECT rolsuper OR rolcreatedb FROM pg_roles WHERE rolname = current_user") { r -> r.getBoolean(1) }.single())
        }
        names.forEach {
            check(it.matches(Regex("tambola_load_(main|journal)_[a-f0-9]{16}")))
            control("CREATE DATABASE $it TEMPLATE template0"); owned += it
        }
        val java = Path.of(System.getProperty("java.home"), "bin", if (System.getProperty("os.name").startsWith("Windows")) "java.exe" else "java")
        val classpath = runtimeLib.toString() + "/*" + if (sqlProfiling)
            System.getProperty("path.separator") + root.resolve("server/build/classes/kotlin/test") else ""
        val builder = ProcessBuilder(java.toString(), "-Xms128m", "-Xmx512m", "-XX:ActiveProcessorCount=4",
            "-Xlog:gc:file=gc.log:time,uptime", "-cp", classpath,
            "io.github.sbshrey.tambola.server." + if (sqlProfiling) "ProfiledCoinServer" else "ServerKt").directory(directory.toFile())
            .redirectOutput(if (sqlProfiling) ProcessBuilder.Redirect.to(directory.resolve("service-diagnostic.log").toFile()) else ProcessBuilder.Redirect.DISCARD)
            .redirectError(ProcessBuilder.Redirect.DISCARD)
        builder.environment().putAll(mapOf("PORT" to port.toString(), "TAMBOLA_BIND_HOST" to "127.0.0.1", "TAMBOLA_LOCAL_DEVELOPMENT" to "true",
            "TAMBOLA_DATABASE_URL" to databaseUrl(0), "TAMBOLA_DATABASE_USER" to user, "TAMBOLA_DATABASE_PASSWORD" to password,
            "TAMBOLA_DELETION_DATABASE_URL" to databaseUrl(1), "TAMBOLA_DELETION_DATABASE_USER" to user, "TAMBOLA_DELETION_DATABASE_PASSWORD" to password))
        listOf("TAMBOLA_PUBLIC_ORIGIN", "TAMBOLA_ANDROID_CERT_SHA256", "TAMBOLA_ANDROID_INSTALL_URL", "TAMBOLA_METRICS_TOKEN")
            .forEach(builder.environment()::remove)
        child = builder.start()
        val http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(1)).build()
        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(30)
        while (true) {
            alive()
            if (runCatching { http.send(HttpRequest.newBuilder(URI("$base/health/ready")).timeout(Duration.ofSeconds(1))
                    .GET().build(), HttpResponse.BodyHandlers.discarding()).statusCode() == 200 }.getOrDefault(false)) return
            check(System.nanoTime() < deadline) { "Owned service did not become ready" }
            delay(100)
        }
    }

    /** Registration/IP-abuse limits are outside this gameplay load; every wallet/purchase uses the API. */
    fun seed(count: Int): List<GuestCredentials> {
        require(count in 8..320)
        val players = List(count) { GuestCredentials(UUID.randomUUID().toString(), secret(), System.currentTimeMillis() + SESSION_LIFETIME) }
        primary { connection ->
            connection.autoCommit = false
            connection.prepareStatement("INSERT INTO guests(id, name, avatar, token_hash, expires_at) VALUES (?, ?, ?, ?, ?)").use { s ->
                players.forEachIndexed { index, player ->
                    listOf(player.playerId, "Coin load ${index + 1}", index % AVATAR_COUNT, digest(player.token), player.expiresAt)
                        .forEachIndexed { n, value -> s.setObject(n + 1, value) }
                    s.addBatch()
                }
                s.executeBatch()
            }
            connection.commit()
        }
        return players
    }

    override fun close() {
        var complete = true
        child?.let { if (it.isAlive) { it.destroyForcibly(); if (!it.waitFor(10, TimeUnit.SECONDS)) complete = false } }
        for (name in owned.toList()) {
            check(name in names && name.matches(Regex("tambola_load_(main|journal)_[a-f0-9]{16}")))
            runCatching { control("DROP DATABASE $name WITH (FORCE)"); owned.remove(name) }.onFailure { complete = false }
        }
        check(complete && owned.isEmpty()) { "Owned load resources need cleanup" }
    }
}
