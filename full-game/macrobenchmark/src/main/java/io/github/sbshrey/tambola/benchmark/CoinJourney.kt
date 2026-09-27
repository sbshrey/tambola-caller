package io.github.sbshrey.tambola.benchmark

import android.content.Context
import android.os.SystemClock
import androidx.test.uiautomator.*
import io.github.sbshrey.tambola.client.HttpRoomApi
import io.github.sbshrey.tambola.domain.*
import io.github.sbshrey.tambola.protocol.*
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import java.io.File
import java.util.UUID
import java.util.regex.Pattern

/** All actions for the measured player go through the visible app; peers supply only public calls/awards. */
internal class CoinJourney(private val context: Context, private val device: UiDevice, private val expectedRounds: Int = 1,
    private val computerOpponents: Boolean = false,
    private val target: String = "io.github.sbshrey.tambola.game",
    private val friendTable: Boolean = false,
    discoveryUrl: String? = null) : AutoCloseable {
    private val api = HttpRoomApi(if (discoveryUrl == null) "https://192.168.1.4:8443" else "https://sbshrey.github.io", discoveryUrl = discoveryUrl)
    private val peers = mutableListOf<GuestCredentials>()
    private var code: String? = null
    private var mainId: String? = null
    private var createdMain = false
    private val cards = linkedMapOf<Int, Ticket>()
    private val marks = mutableSetOf<Int>()
    private val usedHouses = mutableSetOf<Int>()
    private val report = JSONObject().put("completed", false)
        .put("scope", "Non-debuggable optimized app; external UI actions; real HTTPS/WSS round; " +
            if (computerOpponents) "one passive HTTP QA peer and two labelled computer opponents" else "three passive HTTP QA peers")
    private val started = SystemClock.elapsedRealtime()
    private var roundStarted = started
    private var roundNumber = 0
    private var balanceBefore = 0L
    private var expectedPool = 0L
    private var expectedAggregate = 0L
    private var expectedPrizeCount = 0
    private var previousBalance: Long? = null
    private val roundReports = JSONArray()
    private val memorySamples = JSONArray()
    private val originalIdle = Configurator.getInstance().waitForIdleTimeout

    init {
        require(target in setOf("io.github.sbshrey.tambola.game", "io.github.sbshrey.tambola.game.beta"))
        require(expectedRounds in listOf(1, 9))
        require(!computerOpponents || expectedRounds == 1)
        report.put("expectedRounds", expectedRounds).put("roundReports", roundReports).put("memorySamples", memorySamples)
        listOf("coin-release-results.png", "coin-release-failure.png", "coin-release-failure.xml", "friends-replay-ready.png")
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
    private fun tap(tag: String) {
        until { find(tag)?.isEnabled == true }
        val control = node(tag)
        check(control.isEnabled) { "$tag is disabled" }
        control.click()
    }
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

    private fun verifyEmptyProfile() {
        node("lobby-welcome-heading")
        tap("coin-wallet")
        // This dialog has its own accessibility root, so use its visible heading.
        assertTrue("Fresh player setup must be shown",
            device.wait(Until.hasObject(By.text("Choose your player")), 10_000))
        assertFalse("Existing wallets must never be used by this fixture", device.hasObject(By.text("Delete online profile")))
        textButton("Keep playing").click()
        node("lobby-welcome-heading")
        assertEquals(1500L, balance())
    }

    fun prepare() = captureFailure {
        Configurator.getInstance().waitForIdleTimeout = 100
        roundStarted = SystemClock.elapsedRealtime()
        roundNumber++
        check(roundNumber <= expectedRounds)
        cards.clear(); marks.clear(); usedHouses.clear(); code = null
        report.put("roundNumber", roundNumber).put("calls", 0).put("claims", 0)
        checkpoint(if (createdMain) "next-round-wallet-check" else "fresh-profile-check")
        node("coin-play")
        if (!createdMain) {
            verifyEmptyProfile()
            // Create peers before the 12-second lobby begins; their credentials stay only in memory.
            runBlocking { repeat(if (computerOpponents) 1 else 3) { peers += api.guest(GuestRequest("Release QA ${it + 1}", it + 1)) } }
        } else {
            assertEquals(previousBalance, balance())
            (1..6).forEach { assertEquals("Remembered replay choice $it", it == 3, node("buy-tickets-$it").isChecked) }
        }
        balanceBefore = balance()
        val peerWallets = runBlocking { peers.map { peer ->
            val wallet = api.wallet(peer.token)
            if (wallet.balance < 100) {
                val request = RefillRequest(UUID.randomUUID().toString())
                val refilled = api.refill(peer.token, request)
                assertEquals(wallet.balance + 500, refilled.balance)
                assertEquals(refilled, api.refill(peer.token, request))
                refilled
            } else wallet
        } }
        val quantities = peerWallets.map { (it.balance / 100).coerceAtMost(6).toInt().also { count -> check(count in 1..6) } }
        if (computerOpponents) assertEquals(1500L, peerWallets.single().balance)
        expectedPool = 600 + quantities.sum() * 100L + if (computerOpponents) 600 else 0
        expectedAggregate = balanceBefore + peerWallets.sumOf { it.balance }
        report.put("peerTicketQuantities", JSONArray(quantities)).put("expectedPool", expectedPool).put("balanceBefore", balanceBefore)
        if (expectedRounds > 1) recordMemory("entry")
        tap("buy-tickets-6")
        if (friendTable) {
            tap("play-friends")
            // Dialogs have their own accessibility root; use the actual visible button.
            val create = textButton("Create table")
            createdMain = true
            create.click()
            code = textOf(node("friend-code")).replace(" ", "")
            check(requireNotNull(code).matches(Regex("[A-HJ-NP-Z2-9]{8}")))
            report.put("friendTable", true)
        } else { createdMain = true; tap("coin-play") }
        node(if (friendTable) "friend-waiting" else "cancel-match")
        assertEquals(balanceBefore - 600, balance())
        runBlocking {
            for ((index, peer) in peers.withIndex()) {
                val request = MatchRequest(UUID.randomUUID().toString(), quantities[index], friendTable, code.takeIf { friendTable })
                val purchase = api.match(peer.token, request)
                assertEquals(purchase, api.match(peer.token, request))
                val room = purchase.snapshot
                if (code == null) code = room.code else assertEquals(code, room.code)
            }
        }
        val lobby = snapshot()
        assertEquals(if (computerOpponents) 2 else 4, lobby.members.size)
        assertEquals(if (computerOpponents) 2 else 0, lobby.options.computerPlayers)
        val currentMain = lobby.members.single { member -> peers.none { it.playerId == member.playerId } }.playerId
        if (mainId == null) mainId = currentMain else assertEquals("Same identity across rounds", mainId, currentMain)
        assertEquals(expectedPool, lobby.coins!!.pool)
        expectedPrizeCount = lobby.coins!!.prizes.size
        assertTrue(expectedPrizeCount in 6..8)
        if (friendTable) {
            assertTrue(lobby.coins!!.friendTable)
            assertNull(lobby.coins!!.startsAt)
            device.takeScreenshot(File(context.filesDir, "friends-public-ready.png"))
            tap("friend-start")
        }
        node("play-arena", 20_000)
        if (computerOpponents) {
            val round = snapshot().round!!
            assertEquals(4, round.players.size)
            assertEquals(2, round.players.count { it.computer })
            assertTrue(round.players.filter { it.computer }.all { round.ticketCounts[it.id] == 3 })
            assertTrue(description(node("table-players")).contains("2 computer"))
            report.put("labelledComputers", 2).put("computerFundedCoins", 600)
        }
        checkpoint("playing")
    }

    /** External links must review first and preserve already purchased tables. */
    fun verifyFriendInvitation() = captureFailure {
        report.put("scope", "Optimized public beta: cold/warm external invitations, explicit ticket purchase, duplicate/occupied guards and refund; one HTTP QA host")
        Configurator.getInstance().waitForIdleTimeout = 100
        checkpoint("invitation-empty-profile")
        node("coin-play"); verifyEmptyProfile()
        runBlocking {
            peers += api.guest(GuestRequest("Invitation QA host", 2))
            code = api.match(peers.first().token, MatchRequest(UUID.randomUUID().toString(), 3, true)).snapshot.code
        }
        fun open(inviteCode: String) {
            check(inviteCode.matches(Regex("[A-HJ-NP-Z2-9]{8}")))
            device.executeShellCommand("am start -W -a android.intent.action.VIEW -d tambola-beta://friends/$inviteCode -p $target")
            node("friend-invitation-code")
        }
        checkpoint("cold-invitation-review")
        device.executeShellCommand("am force-stop $target")
        open(requireNotNull(code))
        node("friend-invitation-join")
        (1..6).forEach { assertTrue("Ticket choice $it is visible before scrolling", node("invite-tickets-$it").visibleBounds.height() > 0) }
        device.takeScreenshot(File(context.filesDir, "friend-link-review.png"))
        assertEquals(1, snapshot().members.size)
        tap("friend-invitation-dismiss"); verifyEmptyProfile()
        checkpoint("warm-invitation-purchase")
        open(requireNotNull(code))
        node("friend-invitation-scroll").scroll(Direction.DOWN, .6f)
        tap("invite-tickets-2")
        assertTrue(textOf(node("friend-invitation-join")).contains("200"))
        createdMain = true
        tap("friend-invitation-join")
        node("friend-waiting", 30_000)
        until(30_000) { balance() == 1300L && snapshot().members.size == 2 }
        assertEquals(500L, snapshot().coins!!.pool)
        device.takeScreenshot(File(context.filesDir, "friend-link-joined.png"))
        checkpoint("same-table-invitation")
        device.executeShellCommand("am force-stop $target")
        open(requireNotNull(code))
        node("friend-invitation-resume")
        assertNull(find("friend-invitation-join"))
        tap("friend-invitation-resume"); node("friend-waiting")
        assertEquals(1300L, balance()); assertEquals(500L, snapshot().coins!!.pool)
        checkpoint("other-table-invitation")
        open(if (code == "ABCDEFG2") "ABCDEFG3" else "ABCDEFG2")
        node("friend-invitation-resume")
        assertNull(find("friend-invitation-join"))
        device.takeScreenshot(File(context.filesDir, "friend-link-occupied.png"))
        tap("friend-invitation-resume"); node("friend-waiting")
        assertEquals(1300L, balance()); assertEquals(500L, snapshot().coins!!.pool)
        tap("cancel-match")
        until(30_000) { find("coin-play")?.isEnabled == true && balance() == 1500L }
        assertEquals(300L, snapshot().coins!!.pool)
        report.put("openingDidNotRegisterOrJoin", true).put("selectedTickets", 2).put("walletAfterPurchase", 1300)
            .put("duplicateAndOtherInvitationDidNotCharge", true).put("walletAfterRefund", 1500).put("completed", true)
        checkpoint("invitation-passed")
    }

    /** Focused acceptance of a purchase, enabled cancellation, exact refund and cold wallet recovery. */
    fun verifyPurchaseRefund() = captureFailure {
        report.put("scope", "External native UI purchase/cancellation/refund and cold wallet recovery over public HTTPS; no full round in this focused test")
        checkpoint("fresh-purchase-refund-profile")
        node("coin-play")
        verifyEmptyProfile()
        assertEquals(1500L, balance())
        tap("buy-tickets-3")
        createdMain = true
        tap("coin-play")
        until { find("cancel-match")?.isEnabled == true }
        assertEquals(1200L, balance())
        tap("cancel-match")
        until { find("coin-play")?.isEnabled == true && balance() == 1500L }
        assertTrue(node("buy-tickets-3").isChecked)
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
        until(30_000) { find("coin-play")?.isEnabled == true && balance() == 1500L }
        assertTrue(node("buy-tickets-3").isChecked)
        assertNull(find("lobby-welcome-heading"))
        report.put("purchaseCoins", 300).put("walletAfterPurchase", 1200).put("walletAfterRefund", 1500)
            .put("walletAfterColdRestart", 1500).put("rememberedThreeTickets", true).put("completed", true)
        checkpoint("purchase-refund-verified")
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

    /** A new call/finished round deliberately dismisses the picker; never retry a click blindly. */
    private fun clickPrizeChoice(choice: UiObject2?, before: RoomView): RoomView? {
        fun dismissedByGame(): RoomView? {
            val after = snapshot()
            if (after.phase == RoomPhase.ACTIVE && after.round!!.called.size == before.round!!.called.size) return null
            report.put("pickerCallRollovers", report.optInt("pickerCallRollovers") + 1)
            return after
        }
        if (choice == null) return dismissedByGame() ?: error("Prize picker missing within the same call")
        try {
            if (!choice.isEnabled) return dismissedByGame() ?: error("Selected prize disabled within the same call")
            choice.click()
        } catch (stale: StaleObjectException) {
            return dismissedByGame() ?: throw stale
        }
        return null
    }

    /** Deterministically reproduce the original stale read using a real five-second call. */
    fun verifyPickerCallBoundary() = captureFailure {
        checkpoint("picker-boundary")
        until(15_000, 250) { find("claim-ticket-1")?.isEnabled == true }
        val before = snapshot()
        showTicket(1)
        tap("claim-ticket-1")
        val choice = node("claim-prize-EARLY_FIVE")
        assertTrue(choice.isEnabled)
        until(10_000, 250) { snapshot().round!!.called.size != before.round!!.called.size }
        until { find("claim-prize-EARLY_FIVE") == null }
        var originalReadFailed = false
        try { choice.isEnabled } catch (_: StaleObjectException) { originalReadFailed = true }
        assertTrue("The original enabled-state read must reproduce the stale-node failure", originalReadFailed)
        val after = checkNotNull(clickPrizeChoice(choice, before))
        assertTrue(after.round!!.called.size > before.round!!.called.size)
        assertTrue("A dismissed choice must not submit a claim", after.round!!.awards.isEmpty())
        tap("claim-ticket-1")
        assertTrue(node("claim-prize-EARLY_FIVE").isEnabled)
        tap("dismiss-claim")
        report.put("originalStaleReadReproduced", true).put("pickerCallBoundaryVerified", true)
            .put("beforeCall", before.round!!.called.size).put("afterCall", after.round!!.called.size).put("completed", true)
        device.takeScreenshot(File(context.filesDir, "coin-release-results.png"))
        checkpoint("picker-boundary-passed")
    }

    fun play() = captureFailure {
            readCards()
            var seen = 0
            var lastCallDescription = ""
            var reopened = false
            var restoredMarks = 0
            while (true) {
                check(SystemClock.elapsedRealtime() - roundStarted < 540_000) { "Round exceeded real-time limit" }
                if (find("coin-winnings") != null) break
                val shownCall = try { description(find("current-call")) } catch (_: StaleObjectException) {
                    // Results can remove this node between the lookup and read. Only
                    // retry this observation; never repeat a dab or claim action here.
                    report.put("callReadRetries", report.optInt("callReadRetries") + 1)
                    SystemClock.sleep(80)
                    continue
                }
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
                    val advanced = clickPrizeChoice(choice, room)
                    if (advanced != null) room = advanced else until(interval = 250) {
                        room = snapshot()
                        val after = room.round!!
                        after.awards.any { it.prize == prize } || after.called.size != current.called.size
                    }
                    val award = room.round!!.awards.firstOrNull { it.prize == prize }
                    if (award != null) {
                        if (!computerOpponents) assertEquals(listOf(mainId), award.playerIds)
                        val selectedWon = room.round!!.winningTickets.any {
                            it.id in award.ticketIds && it.playerId == mainId && it.ordinal == entry.key
                        }
                        if (prize.isRankedHouse && selectedWon) usedHouses += entry.key
                    }
                }
                if (!reopened && seen >= 20) {
                    // Keep most endurance rounds in one process; one cold recovery remains deliberate.
                    val cold = expectedRounds == 1 || roundNumber == 3
                    if (cold) device.executeShellCommand("am force-stop $target")
                    else { device.pressHome(); SystemClock.sleep(1500) }
                    device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
                    until(30_000) { find("play-arena") != null || find("resume-match") != null || find("coin-retry") != null }
                    if (find("coin-retry") != null) {
                        // Process death may interrupt a claim before its receipt is saved. Use the
                        // existing explicit retry, preserving the original request identity.
                        until(30_000) { find("coin-retry")?.isEnabled == true }
                        tap("coin-retry")
                        report.put("coldPendingReceiptRetried", true)
                        until(30_000) { find("play-arena") != null || find("resume-match") != null }
                    }
                    if (find("resume-match") != null) tap("resume-match")
                    node("play-arena")
                    for (number in marks) {
                        showTicket(cards.entries.single { number in it.value.numbers }.key)
                        assertEquals("Unmark $number", description(node("dab-$number")))
                    }
                    reopened = true
                    restoredMarks = marks.size
                    report.put(if (cold) "coldProcessRestoredMarks" else "foregroundRestoredMarks", marks.size)
                    if (expectedRounds > 1) recordMemory(if (cold) "cold-restored" else "foreground-restored")
                }
                report.put("calls", seen).put("claims", room.round!!.awards.size)
                checkpoint("playing")
            }
            val result = snapshot()
            assertEquals(expectedPrizeCount, result.round!!.awards.size)
            if (!computerOpponents) assertTrue(result.round!!.awards.all { it.playerIds == listOf(mainId) })
            val winnings = verifiedWinnings(result)
            val ownWinnings = winnings.getOrDefault(requireNotNull(mainId), 0L)
            val computerWinnings = result.round!!.players.filter { it.computer }.sumOf { winnings.getOrDefault(it.id, 0L) }
            if (computerOpponents) {
                report.put("computerWinnings", computerWinnings).put("humanWinnings", expectedPool - computerWinnings)
                // Computers fund their virtual tickets, then retain their own winnings; no human wallet owns those coins.
                expectedAggregate += 600 - computerWinnings
            }
            node("coin-winnings")
            assertEquals("$ownWinnings coins won", textOf(node("coin-winnings")))
            val finalBalance = balanceBefore - 600 + ownWinnings
            assertEquals(finalBalance, balance())
            assertEquals(expectedAggregate, balance() + runBlocking { peers.sumOf { api.wallet(it.token).balance } })
            report.put("finishedCalls", result.round!!.called.size).put("settledWinnings", ownWinnings).put("finalBalance", finalBalance)
            // Compose exposes a selected non-tab choice as Android's checked state.
            (1..6).forEach { assertEquals("Ticket choice $it", it == 6, node("buy-tickets-$it").isChecked) }
            assertTrue(textOf(node(if (friendTable) "friend-replay" else "coin-play")).contains("600"))
            report.put("rememberedSixTickets", true)
            device.takeScreenshot(File(context.filesDir, "coin-release-results.png"))
            if (friendTable) verifyFriendsReplay(result, finalBalance)
            tap("buy-tickets-3"); tap("coin-play"); node("cancel-match")
            assertEquals(finalBalance - 300, balance())
            tap("cancel-match"); node("coin-play")
            until { balance() == finalBalance }
            (1..6).forEach { assertEquals("Ticket choice $it", it == 3, node("buy-tickets-$it").isChecked) }
            previousBalance = finalBalance
            roundReports.put(JSONObject().put("round", roundNumber).put("calls", result.round!!.called.size)
                .put("prizes", expectedPrizeCount).put("pool", expectedPool).put("balanceBefore", balanceBefore)
                .put("finalBalance", finalBalance).put("aggregateBalance", expectedAggregate)
                .put("durationMs", SystemClock.elapsedRealtime() - roundStarted)
                .put("peerTicketQuantities", report.getJSONArray("peerTicketQuantities"))
                .put("reconnect", if (expectedRounds == 1 || roundNumber == 3) "process-death" else "foreground")
                .put("retainedMarks", restoredMarks).put("replayQuantityAndRefund", true))
            if (expectedRounds > 1) recordMemory("round-complete")
            report.put("newRoundPurchasedAndRefunded", true).put("roundsCompleted", roundReports.length())
                .put("completed", expectedRounds == 1)
            checkpoint(if (expectedRounds == 1) "passed" else "round-passed")
    }

    private fun verifyFriendsReplay(previous: RoomView, finalBalance: Long) {
        checkpoint("friends-replay")
        tap("buy-tickets-3")
        assertTrue(textOf(node("friend-replay")).contains("300"))
        tap("friend-replay"); node("cancel-match")
        until { balance() == finalBalance - 300 }
        val peer = peers.first()
        val before = runBlocking { api.wallet(peer.token).balance }
        val request = MatchRequest(UUID.randomUUID().toString(), 2, true, previous.code, previous.round!!.id)
        val receipt = runBlocking { api.match(peer.token, request) }
        val next = receipt.snapshot
        assertEquals(receipt, runBlocking { api.match(peer.token, request) })
        assertNotEquals(previous.roomId, next.roomId)
        assertEquals(setOf(requireNotNull(mainId), peer.playerId), next.members.map { it.playerId }.toSet())
        assertEquals(500L, next.coins!!.pool)
        assertEquals(2, next.coins!!.ownTickets)
        assertEquals(0, next.options.computerPlayers)
        assertEquals(before - 200, runBlocking { api.wallet(peer.token).balance })
        until { find("friend-start")?.isEnabled == true }
        device.takeScreenshot(File(context.filesDir, "friends-replay-ready.png"))
        // A fresh process must resume these paid tickets, not submit another replay.
        device.executeShellCommand("am force-stop $target")
        device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
        until(25_000) { find("friend-start")?.isEnabled == true }
        assertEquals(finalBalance - 300, balance())
        tap("cancel-match"); node("coin-play")
        until { balance() == finalBalance }
        val remaining = runBlocking { api.read(peer.token, next.code).snapshot }
        assertEquals(peer.playerId, remaining.hostId)
        runBlocking { api.command(peer.token, next.code, CommandRequest(UUID.randomUUID().toString(), remaining.revision, RoomAction.Leave)) }
        assertEquals(before, runBlocking { api.wallet(peer.token).balance })
        assertEquals(previous.round!!.id, runBlocking { api.read(peer.token, previous.code).snapshot.round!!.id })
        report.put("sameFriendsReplay", JSONObject().put("players", 2).put("pool", 500)
            .put("ownTickets", 3).put("peerTickets", 2).put("exactPeerReceipt", true)
            .put("processRecovery", true).put("bothPurchasesRefunded", true))
    }

    /** Independent integer split using public winner identities; never reads opponents' ticket numbers. */
    private fun verifiedWinnings(result: RoomView): Map<String, Long> {
        assertEquals(RoomPhase.FINISHED, result.phase)
        val round = requireNotNull(result.round)
        val pool = requireNotNull(result.coins)
        assertEquals(expectedPool, pool.pool)
        assertEquals(expectedPool, pool.prizes.sumOf { it.coins })
        val tickets = round.winningTickets.associateBy { it.id }
        val totals = mutableMapOf<String, Long>()
        val prizes = JSONArray()
        for (slot in pool.prizes) {
            val award = round.awards.single { it.prize == slot.prize }
            val ordered = award.ticketIds.sorted()
            assertTrue(ordered.isNotEmpty())
            assertEquals(ordered.size, ordered.distinct().size)
            ordered.forEachIndexed { index, id ->
                val winner = tickets.getValue(id).playerId
                assertTrue(winner in award.playerIds)
                val share = slot.coins / ordered.size + if (index < slot.coins % ordered.size) 1 else 0
                totals[winner] = totals.getOrDefault(winner, 0L) + share
            }
            prizes.put(JSONObject().put("prize", slot.prize.name).put("coins", slot.coins)
                .put("winningTickets", ordered.size).put("computerTickets", ordered.count { id ->
                    round.players.single { it.id == tickets.getValue(id).playerId }.computer
                }).put("ownTickets", ordered.count { tickets.getValue(it).playerId == mainId }))
        }
        assertEquals(expectedPool, totals.values.sum())
        assertEquals(0L, pool.returnedCoins)
        peers.forEach { peer ->
            val view = runBlocking { api.read(peer.token, requireNotNull(code)).snapshot }
            assertEquals(totals.getOrDefault(peer.playerId, 0L), view.coins!!.settledWinnings)
            assertEquals(0L, view.coins!!.returnedCoins)
            if (computerOpponents) assertEquals(900L + totals.getOrDefault(peer.playerId, 0L), view.wallet!!.balance)
        }
        report.put("publicWinnerSharesVerified", true).put("prizeWinners", prizes)
        return totals
    }

    fun finishEndurance() = captureFailure {
        assertEquals(9, expectedRounds)
        assertEquals(expectedRounds, roundReports.length())
        val roundDuration = (0 until roundReports.length()).sumOf { roundReports.getJSONObject(it).getLong("durationMs") }
        assertTrue("Native rounds must span at least one hour, excluding trace processing", roundDuration >= 3_600_000)
        report.put("measuredRoundDurationMs", roundDuration).put("minimumHourVerified", true).put("completed", true)
        checkpoint("passed")
    }

    private fun recordMemory(stage: String) {
        val output = device.executeShellCommand("dumpsys meminfo -s $target")
        fun value(pattern: String) = checkNotNull(Regex(pattern).find(output)) { "Missing memory diagnostic" }.groupValues[1].toLong()
        memorySamples.put(JSONObject().put("round", roundNumber).put("stage", stage)
            .put("elapsedMs", SystemClock.elapsedRealtime() - started)
            .put("pid", value("MEMINFO in pid (\\d+)"))
            .put("totalPssKb", value("TOTAL PSS:\\s+(\\d+)"))
            .put("javaHeapPssKb", value("Java Heap:\\s+(\\d+)"))
            .put("nativeHeapPssKb", value("Native Heap:\\s+(\\d+)"))
            .put("activities", value("Activities:\\s+(\\d+)")))
    }

    private fun captureFailure(action: () -> Unit) {
        try { action() } catch (error: Throwable) {
            report.put("failureType", error.javaClass.simpleName).put("completed", false)
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
                device.executeShellCommand("am start -n $target/io.github.sbshrey.tambola.game.MainActivity")
                if (find("friend-invitation-dismiss") != null) tap("friend-invitation-dismiss")
                until(20_000) { find("coin-wallet") != null || find("play-arena") != null }
                repeat(4) { if (find("coin-wallet") == null) { device.pressBack(); SystemClock.sleep(150) } }
                tap("coin-wallet")
                textButton("Delete online profile").click()
                assertTrue(device.wait(Until.hasObject(By.text("Keep playing")), 10_000))
                textButton("Delete online profile").click()
                node("coin-play")
                until { balance() == 1500L }
                verifyEmptyProfile()
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
