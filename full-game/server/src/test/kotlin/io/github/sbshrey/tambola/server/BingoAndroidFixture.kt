package io.github.sbshrey.tambola.server

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import io.ktor.server.routing.*
import io.ktor.server.response.*
import io.ktor.http.*
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/** Opt-in emulator acceptance against an isolated test schema, never the installed host. */
object BingoAndroidFixture : PostgresTest() {
    @JvmStatic fun main(args: Array<String>) {
        check(args.isEmpty())
        val recovery = System.getenv("TAMBOLA_BINGO_RECOVERY") == "true"
        for (port in if (recovery) listOf(8080, 8081, 8082) else listOf(8080)) ServerSocket().use { it.bind(java.net.InetSocketAddress("127.0.0.1", port)) }
        val adb = File(System.getenv("LOCALAPPDATA"), "Android/Sdk/platform-tools/adb.exe").absolutePath
        fun run(vararg arguments: String): String {
            val output = File(".test-workspace/bingo-adb-output.txt")
            val process = ProcessBuilder(listOf(adb, "-s", "emulator-5582") + arguments).redirectErrorStream(true).redirectOutput(output).start()
            try {
                check(process.waitFor(if (arguments.first() == "shell") 180 else 30, TimeUnit.SECONDS) && process.exitValue() == 0) { "Emulator operation failed" }
                return output.readText()
            } finally { if (process.isAlive) process.destroyForcibly().waitFor(5, TimeUnit.SECONDS) }
        }
        check(run("get-state").trim() == "device")
        check(!run("reverse", "--list").contains("tcp:8080"))
        if (recovery) check(!run("reverse", "--list").contains("tcp:8082"))
        prepareDatabase()
        now.set(System.currentTimeMillis())
        service = if (recovery) RoomService(database, now::get) else RoomService(database)
        val server = embeddedServer(Netty, host = "127.0.0.1", port = if (recovery) 8081 else 8080) {
            roomsModule(database, service, runWorker = !recovery)
            if (recovery) routing {
                post("/qa/bingo/tick/{count}") {
                    val count = requireNotNull(call.parameters["count"]?.toIntOrNull()).also { require(it in 1..75) }
                    repeat(count) {
                        now.addAndGet(BINGO_INTERVAL)
                        // A concurrent native poll may hold the room lock; the production worker skips it.
                        val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5)
                        while (service.tick() == 0) { check(System.nanoTime() < deadline); Thread.sleep(10) }
                    }
                    call.respond(HttpStatusCode.NoContent)
                }
            }
        }
        var mapped = false
        var controlMapped = false
        var proxy: Process? = null
        try {
            server.start(wait = false)
            if (recovery) {
                proxy = ProcessBuilder("node", "tools/room-fault-proxy.mjs").redirectErrorStream(true).redirectOutput(File(".test-workspace/bingo-fault-proxy.log")).start()
                val deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10)
                while (!runCatching { java.net.Socket("127.0.0.1", 8082).use { } }.isSuccess) {
                    check(proxy.isAlive && System.nanoTime() < deadline); Thread.sleep(100)
                }
                run("reverse", "tcp:8082", "tcp:8082"); controlMapped = true
            }
            run("reverse", "tcp:8080", "tcp:8080"); mapped = true
            run("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
            run("install", "-r", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
            val result = run("shell", "am", "instrument", "-w", "-e", "bingoFixture", "true", "-e", "class",
                "io.github.sbshrey.tambola.game.${if (recovery) "BingoRecoveryNativeTest" else "BingoOnlineNativeTest"}", "io.github.sbshrey.tambola.game.test/androidx.test.runner.AndroidJUnitRunner")
            File(".test-workspace/${if (recovery) "bingo-recovery-test" else "bingo-native-online-test"}.txt").writeText(result)
            check(result.contains("OK (1 test)") && !result.contains("FAILURES")) { "Native Bingo test failed; inspect the matching native test report in .test-workspace" }
            check(database.transaction { it.query("SELECT count(*) FROM guests") { row -> row.getInt(1) }.single() } == 0)
            println("Native Bingo purchase, mark, restore and profile cleanup passed on an isolated schema.")
        } finally {
            if (mapped) run("reverse", "--remove", "tcp:8080")
            if (controlMapped) run("reverse", "--remove", "tcp:8082")
            proxy?.takeIf { it.isAlive }?.destroyForcibly()?.waitFor(5, TimeUnit.SECONDS)
            server.stop(100, 1000)
            disposeDatabase()
        }
    }
}
