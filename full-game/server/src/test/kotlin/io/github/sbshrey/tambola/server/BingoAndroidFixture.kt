package io.github.sbshrey.tambola.server

import io.ktor.server.engine.*
import io.ktor.server.netty.*
import java.io.File
import java.net.ServerSocket
import java.util.concurrent.TimeUnit

/** Opt-in emulator acceptance against an isolated test schema, never the installed host. */
object BingoAndroidFixture : PostgresTest() {
    @JvmStatic fun main(args: Array<String>) {
        check(args.isEmpty())
        ServerSocket().use { it.bind(java.net.InetSocketAddress("127.0.0.1", 8080)) }
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
        prepareDatabase()
        service = RoomService(database)
        val server = embeddedServer(Netty, host = "127.0.0.1", port = 8080) { roomsModule(database, service) }
        var mapped = false
        try {
            server.start(wait = false)
            run("reverse", "tcp:8080", "tcp:8080"); mapped = true
            run("install", "-r", "app/build/outputs/apk/debug/app-debug.apk")
            run("install", "-r", "app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk")
            val result = run("shell", "am", "instrument", "-w", "-e", "bingoFixture", "true", "-e", "class",
                "io.github.sbshrey.tambola.game.BingoOnlineNativeTest", "io.github.sbshrey.tambola.game.test/androidx.test.runner.AndroidJUnitRunner")
            File(".test-workspace/bingo-native-online-test.txt").writeText(result)
            check(result.contains("OK (1 test)") && !result.contains("FAILURES")) { "Native Bingo test failed; see .test-workspace/bingo-native-online-test.txt" }
            check(database.transaction { it.query("SELECT count(*) FROM guests") { row -> row.getInt(1) }.single() } == 0)
            println("Native Bingo purchase, mark, restore and profile cleanup passed on an isolated schema.")
        } finally {
            if (mapped) run("reverse", "--remove", "tcp:8080")
            server.stop(100, 1000)
            disposeDatabase()
        }
    }
}
