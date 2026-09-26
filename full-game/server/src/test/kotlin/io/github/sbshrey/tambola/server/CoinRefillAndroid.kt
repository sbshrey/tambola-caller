package io.github.sbshrey.tambola.server

import io.ktor.http.*
import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.io.File
import java.net.HttpURLConnection
import java.net.URI
import java.net.ServerSocket
import java.util.UUID
import java.util.concurrent.TimeUnit

/** Opt-in native refill test. Its spending route exists only in a fresh test schema and test classpath. */
object CoinRefillAndroid : PostgresTest() {
    @JvmStatic fun main(args: Array<String>) {
        check(args.isEmpty())
        check(System.getenv("TAMBOLA_DATABASE_URL").orEmpty().matches(Regex("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/tambola_test")))
        val sdk = System.getenv("ANDROID_SDK_ROOT") ?: "${System.getenv("LOCALAPPDATA")}/Android/Sdk"
        val adb = File(sdk, "platform-tools/adb.exe").absolutePath
        val serial = "emulator-5582"
        fun command(vararg arguments: String): String {
            val process = ProcessBuilder(arguments.toList()).redirectErrorStream(true).start()
            try {
                check(process.waitFor(30, TimeUnit.SECONDS) && process.exitValue() == 0) { "Fixture command failed: ${arguments.take(3)}" }
                return process.inputStream.bufferedReader().readText()
            } finally { if (process.isAlive) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS) }
        }
        fun adb(vararg arguments: String) = command(adb, "-s", serial, *arguments)
        check(adb("emu", "avd", "name").lineSequence().first().startsWith("tambola_full_game_"))
        check(!Regex("tcp:808[02]").containsMatchIn(adb("reverse", "--list")))
        for (port in listOf(8080, 8081, 8082)) ServerSocket().use { it.bind(java.net.InetSocketAddress("127.0.0.1", port)) }
        prepareDatabase()
        var proxy: Process? = null
        var driver: Process? = null
        val mappings = mutableListOf<String>()
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 8081) {
            service = RoomService(database) // Real clock; no shortened cooldown or game pace.
            roomsModule(database, service)
            routing {
                post("/qa/spend/{player}") {
                    val player = UUID.fromString(call.parameters["player"]).toString()
                    database.transaction { connection ->
                        check(connection.query("SELECT name FROM guests WHERE id = ?", player) { it.getString(1) }.single() == "Refill QA")
                        val wallet = CoinLedger.view(connection, player)
                        check(wallet.balance > 0)
                        CoinLedger.change(connection, player, "qa:${UUID.randomUUID()}", -wallet.balance, System.currentTimeMillis())
                    }
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
        try {
            server.start(wait = false)
            proxy = ProcessBuilder("node", "tools/room-fault-proxy.mjs").redirectErrorStream(true)
                .redirectOutput(File(".test-workspace/refill-proxy.log")).start()
            val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
            while (true) {
                val ready = runCatching {
                    val connection = URI("http://127.0.0.1:8080/health/ready").toURL().openConnection() as HttpURLConnection
                    try { connection.connectTimeout = 500; connection.readTimeout = 500; connection.responseCode == 200 }
                    finally { connection.disconnect() }
                }.getOrDefault(false)
                if (ready) break
                check(proxy.isAlive && System.nanoTime() < deadline) { "Refill fixture did not become ready" }
                Thread.sleep(100)
            }
            for (port in listOf("tcp:8080", "tcp:8082")) { adb("reverse", port, port); mappings += port }
            for (apk in listOf("app/build/outputs/apk/debug/app-debug.apk", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"))
                adb("install", "-r", apk)
            driver = ProcessBuilder("node", "tools/android-smoke.mjs", "--online", "--fault-proxy", "--class",
                "io.github.sbshrey.tambola.game.CoinRefillTest", "--label", "coin-refill-native").inheritIO().start()
            check(driver.waitFor(180, TimeUnit.SECONDS) && driver.exitValue() == 0) { "Native refill test failed" }
            check(database.transaction { it.query("SELECT 1 FROM guests") { true }.isEmpty() }) { "QA profile was not deleted" }
            println("Native refill passed; isolated profile removed; installed host untouched.")
        } finally {
            driver?.takeIf { it.isAlive }?.destroyForcibly()?.waitFor(5, TimeUnit.SECONDS)
            proxy?.takeIf { it.isAlive }?.destroyForcibly()?.waitFor(5, TimeUnit.SECONDS)
            for (port in mappings) adb("reverse", "--remove", port)
            server.stop(100, 1000)
            disposeDatabase()
        }
    }
}
