package io.github.sbshrey.tambola.benchmark

import android.content.Context
import android.os.SystemClock
import androidx.test.uiautomator.*
import io.github.sbshrey.tambola.client.HttpRoomApi
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.regex.Pattern

/** All actions for the measured player go through the visible app; peers supply only public calls/awards. */
internal class CoinJourney(private val context: Context, private val device: UiDevice) : AutoCloseable {
    private val api = HttpRoomApi("https://192.168.1.4:8443")
    private val peers = mutableListOf<GuestCredentials>()
    private var code: String? = null
    private var mainId: String? = null
    private var createdMain = false
    private val cards = linkedMapOf<Int, Ticket>()
    private val marks = mutableSetOf<Int>()
    private val usedHouses = mutableSetOf<Int>()
    private val report = JSONObject().put("completed", false)
        .put("scope", "Non-debuggable optimized app; external UI actions; real HTTPS/WSS round; three passive HTTP QA peers")
    private val started = SystemClock.elapsedRealtime()
    private val originalIdle = Configurator.getInstance().waitForIdleTimeout

    init {
        listOf("coin-release-results.png", "coin-release-failure.png", "coin-release-failure.xml")
            .forEach { File(context.filesDir, it).delete() }
    }

    private fun checkpoint(stage: String) {
        report.put("stage", stage).put("elapsedMs", SystemClock.elapsedRealtime() - started)
        File(context.filesDir, "coin-release-journey.json").writeText(report.toString(2) + "\n")
    }
    private fun until(timeout: Long = 10_000, interval: Long = 80, condition: () -> Boolean) {
        val end = SystemClock.elapsedRealtime() + timeout
        while (!condition()) {
            check(SystemClock.elapsedRealtime() < end) { "UI condition timed out at ${report.optString("stage")}" }
            SystemClock.sleep(interval)
        }
    }
    private fun find(tag: String) = device.findObject(By.res(tag))
    private fun node(tag: String, timeout: Long = 10_000): UiObject2 = checkNotNull(device.wait(Until.findObject(By.res(tag)), timeout)) { "Missing UI control $tag" }
    private fun tap(tag: String) { val control = node(tag); check(control.isEnabled) { "$tag is disabled" }; control.click() }
    private fun textButton(text: String): UiObject2 {
        val labels = checkNotNull(device.wait(Until.findObjects(By.text(text)), 10_000))
        for (label in labels.asReversed()) {
            var ancestor: UiObject2? = label
            while (ancestor != null) {
                // Compose exposes a clickable View with a separate, non-clickable Button child.
                if (ancestor.isClickable && ancestor.isEnabled) return ancestor
                ancestor = ancestor.parent
            }
        }
        error("No button contains $text")
    }
    private fun textOf(control: UiObject2): String = listOfNotNull(control.text).plus(control.children.flatMap { textOf(it).takeIf(String::isNotBlank)?.let(::listOf).orEmpty() }).joinToString(" ")
    private fun description(control: UiObject2?): String = control?.let {
        it.contentDescription?.takeIf(String::isNotBlank)
            ?: it.children.firstNotNullOfOrNull { child -> description(child).takeIf(String::isNotBlank) }
    }.orEmpty()
    private fun balance() = Regex("(\\d+) coins").find(textOf(node("coin-wallet")))!!.groupValues[1].toLong()
    private fun snapshot() = runBlocking { api.read(peers.first().token, requireNotNull(code)).snapshot }

    fun prepare() = captureFailure {
        Configurator.getInstance().waitForIdleTimeout = 100
        checkpoint("fresh-profile-check")
        node("coin-play")
        tap("coin-wallet")
        assertTrue("Use an empty dedicated emulator profile; existing wallets are never reset", device.wait(Until.hasObject(By.text("Your profile")), 5000))
        assertFalse(device.hasObject(By.text("Delete online profile")))
        textButton("Got it").click()
        assertEquals(1500L, balance())
        // Create peers before the 12-second lobby begins; their credentials stay only in memory.
        runBlocking { repeat(3) { peers += api.guest(GuestRequest("Release QA ${it + 1}", it + 1)) } }
        tap("buy-tickets-6")
        createdMain = true
        tap("coin-play")
        node("cancel-match")
        assertEquals(900L, balance())
        runBlocking {
            for (peer in peers) {
                val room = api.match(peer.token, MatchRequest(UUID.randomUUID().toString(), 6)).snapshot
                if (code == null) code = room.code else assertEquals(code, room.code)
            }
        }
        val lobby = snapshot()
        assertEquals(4, lobby.members.size)
        mainId = lobby.members.single { member -> peers.none { it.playerId == member.playerId } }.playerId
        assertEquals(2400L, lobby.coins!!.pool)
        assertEquals(8, lobby.coins!!.prizes.size)
        node("play-arena", 20_000)
        checkpoint("playing")
    }

    private fun showTicket(ordinal: Int) {
        repeat(6) {
            if (find("hand-ticket-$ordinal") != null) return
            val shown = (1..6).first { find("hand-ticket-$it") != null }
            tap(if (ordinal < shown) "tickets-up" else "tickets-down")
            until { find("hand-ticket-$shown") == null }
        }
        error("Ticket page did not appear")
    }

    private fun readCards() {
        for (ordinal in 1..6) {
            showTicket(ordinal)
            val cells = node("hand-ticket-$ordinal").findObjects(By.res(Pattern.compile("dab-\\d+")))
            assertEquals(15, cells.size)
            // Touch targets expand asymmetrically; the printed numbers share exact row centers.
            val rows = cells.groupBy { it.findObject(By.res("ticket-number")).visibleCenter.y }.toSortedMap().values.toList()
            assertEquals(3, rows.size)
            val values = MutableList(27) { 0 }
            rows.forEachIndexed { row, entries ->
                assertEquals(5, entries.size)
                for (entry in entries) {
                    val number = entry.resourceName.removePrefix("dab-").toInt()
                    values[row * 9 + (number / 10).coerceAtMost(8)] = number
                }
            }
            cards[ordinal] = Ticket("ui-$ordinal", "me", values)
        }
        assertEquals(90, cards.values.flatMap { it.numbers }.distinct().size)
        report.put("ownedTickets", 6).put("distinctOwnNumbers", 90)
    }

    fun play() = captureFailure {
            readCards()
            var seen = 0
            var lastCallDescription = ""
            var reopened = false
            while (true) {
                check(SystemClock.elapsedRealtime() - started < 540_000) { "Round exceeded real-time limit" }
                if (find("coin-winnings") != null) break
                val shownCall = description(find("current-call"))
                if (shownCall.isBlank() || shownCall == lastCallDescription) { SystemClock.sleep(200); continue }
                lastCallDescription = shownCall
                var room = snapshot()
                if (room.phase == RoomPhase.FINISHED) break
                val round = requireNotNull(room.round)
                if (round.called.size == seen) { SystemClock.sleep(200); continue }
                for (number in round.called.drop(seen)) {
                    val ordinal = cards.entries.single { number in it.value.numbers }.key
                    showTicket(ordinal)
                    until { find("dab-$number")?.isEnabled == true }
                    if (description(node("dab-$number")) == "Mark $number") tap("dab-$number")
                    until { description(find("dab-$number")) == "Unmark $number" }
                    marks += number
                }
                seen = round.called.size
                for (prize in room.options.game.prizes) {
                    val current = requireNotNull(room.round)
                    if (room.phase != RoomPhase.ACTIVE || current.awards.any { it.prize == prize }) continue
                    val closed = current.awards.filter { it.prize.isRankedHouse && it.drawIndex < current.called.size }
                    val nextHouse = room.options.game.prizes.filter { it.isRankedHouse && closed.none { award -> award.prize == it } }.minByOrNull { it.ordinal }
                    if (prize.isRankedHouse && prize != nextHouse) continue
                    val entry = cards.entries.firstOrNull { prize.matches(it.value, marks) && (!prize.isRankedHouse || it.key !in usedHouses) } ?: continue
                    showTicket(entry.key)
                    tap("claim-ticket-${entry.key}")
                    val choice = device.wait(Until.findObject(By.res("claim-prize-${prize.name}")), 1500)
                    if (choice == null) { if (snapshot().round!!.called.size != current.called.size) continue; error("Prize picker missing") }
                    check(choice.isEnabled)
                    choice.click()
                    until(interval = 250) {
                        room = snapshot()
                        val after = room.round!!
                        after.awards.any { it.prize == prize } || after.called.size != current.called.size
                    }
                    val award = room.round!!.awards.firstOrNull { it.prize == prize }
                    if (award != null) {
                        assertEquals(listOf(mainId), award.playerIds)
                        if (prize.isRankedHouse) usedHouses += entry.key
                    }
                }
                if (!reopened && seen >= 20) {
                    // Cold process restoration checks release serializers, encrypted storage and reconnect.
                    device.executeShellCommand("am force-stop io.github.sbshrey.tambola.game")
                    device.executeShellCommand("am start -n io.github.sbshrey.tambola.game/.MainActivity")
                    until(20_000) { find("play-arena") != null || find("resume-match") != null }
                    if (find("resume-match") != null) tap("resume-match")
                    node("play-arena")
                    for (number in marks) {
                        showTicket(cards.entries.single { number in it.value.numbers }.key)
                        assertEquals("Unmark $number", description(node("dab-$number")))
                    }
                    reopened = true
                    report.put("coldProcessRestoredMarks", marks.size)
                }
                report.put("calls", seen).put("claims", room.round!!.awards.size)
                checkpoint("playing")
            }
            val result = snapshot()
            assertEquals(8, result.round!!.awards.size)
            assertTrue(result.round!!.awards.all { it.playerIds == listOf(mainId) })
            node("coin-winnings")
            assertEquals("2400 coins won", textOf(node("coin-winnings")))
            assertEquals(3300L, balance())
            assertEquals(6000L, balance() + runBlocking { peers.sumOf { api.wallet(it.token).balance } })
            report.put("finishedCalls", result.round!!.called.size).put("settledWinnings", 2400).put("finalBalance", 3300)
            assertTrue(node("buy-tickets-6").isSelected)
            assertTrue(textOf(node("coin-play")).contains("600"))
            report.put("rememberedSixTickets", true)
            device.takeScreenshot(File(context.filesDir, "coin-release-results.png"))
            tap("buy-tickets-3"); tap("coin-play"); node("cancel-match")
            assertEquals(3000L, balance())
            tap("cancel-match"); node("coin-play")
            until { balance() == 3300L }
            assertTrue(node("buy-tickets-3").isSelected)
            report.put("newRoundPurchasedAndRefunded", true).put("completed", true)
            checkpoint("passed")
    }

    private fun captureFailure(action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            report.put("failureType", error.javaClass.simpleName)
            checkpoint("failed")
            device.takeScreenshot(File(context.filesDir, "coin-release-failure.png"))
            device.dumpWindowHierarchy(File(context.filesDir, "coin-release-failure.xml"))
            throw error
        }
    }

    override fun close() {
        try {
            if (createdMain) {
                // Macrobenchmark can stop the measured app before this finally block.
                device.executeShellCommand("am start -n io.github.sbshrey.tambola.game/.MainActivity")
                until(20_000) { find("coin-wallet") != null || find("play-arena") != null }
                repeat(4) { if (find("coin-wallet") == null) { device.pressBack(); SystemClock.sleep(150) } }
                tap("coin-wallet")
                textButton("Delete online profile").click()
                assertTrue(device.wait(Until.hasObject(By.text("Keep playing")), 10_000))
                textButton("Delete online profile").click()
                node("coin-play")
                until { balance() == 1500L }
                tap("coin-wallet")
                assertTrue(device.wait(Until.hasObject(By.text("Your profile")), 10_000))
                assertFalse(device.hasObject(By.text("Delete online profile")))
                textButton("Got it").click()
                report.put("nativeProfileDeleted", true)
            }
        } finally {
            try {
                runBlocking { for (peer in peers) api.deleteProfile(peer.token, DeleteProfileRequest(UUID.randomUUID().toString())) }
                report.put("deletedQaPeers", peers.size)
            } finally {
                api.close()
                Configurator.getInstance().waitForIdleTimeout = originalIdle
                checkpoint(report.optString("stage", "cleanup"))
            }
        }
    }
}
