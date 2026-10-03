@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.sbshrey.tambola.protocol

import io.github.sbshrey.tambola.domain.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 10
val WireJson = Json { encodeDefaults = true }

@Serializable data class GuestRequest(val displayName: String, val avatar: Int = 0)
@Serializable data class GuestCredentials(val playerId: String, val token: String, val expiresAt: Long) {
    override fun toString() = "GuestCredentials(playerId=$playerId, token=redacted, expiresAt=$expiresAt)"
}
@Serializable data class DeleteProfileRequest(val id: String)
@Serializable data class DeleteProfileReceipt(val id: String, val deletedAt: Long, val confirmUntil: Long)
@Serializable data class ApiError(val code: String, val message: String)
@Serializable data class Health(val status: String, val protocolVersion: Int = PROTOCOL_VERSION)
@Serializable enum class RoomPhase { LOBBY, ACTIVE, FINISHED, CLOSED }
@Serializable data class RoomOptions(
    val game: RoundSettings = RoundSettings(mode = GameMode.ONLINE),
    val capacity: Int = 32,
    val intervalSeconds: Int = 10,
    val automaticCalling: Boolean = true,
    val computerPlayers: Int = 0,
    val coinGame: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val coinRulesVersion: Int = 1,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val powersEnabled: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val largeMatch: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val previewPowers: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val roundSummary: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val quickTambola: Boolean = false,
) {
    init {
        require(game.mode == GameMode.ONLINE && capacity in 2..50 && intervalSeconds in 5..30)
        require(coinRulesVersion in 1..2)
        require(!powersEnabled || (coinGame && coinRulesVersion == 2))
        require(!previewPowers || powersEnabled)
        require(!roundSummary || (coinGame && coinRulesVersion == 2))
        require(!quickTambola || (coinGame && coinRulesVersion == 2 && roundSummary && largeMatch && intervalSeconds == 5 &&
            !powersEnabled && game.assistedMarking &&
            game.maxCalls == 60 && game.prizes == listOf(Prize.EARLY_FIVE, Prize.ANY_LINE) && game.winnersPerPrize == 5))
        require(!largeMatch || (coinGame && coinRulesVersion == 2 && capacity == 50))
        require(computerPlayers in 0..(if (largeMatch) 49 else 5) && computerPlayers < capacity)
        require(!coinGame || (game.manualClaims && (!game.assistedMarking || quickTambola) && game.ticketsPerPlayer == 6 &&
            game.customPrizes.isEmpty() && automaticCalling &&
            (intervalSeconds == (if (coinRulesVersion == 1) 5 else 10) || (roundSummary && intervalSeconds == 8) || quickTambola)))
    }
}
@Serializable data class CreateRoomRequest(val id: String, val options: RoomOptions = RoomOptions())
@Serializable data class MemberView(val playerId: String, val displayName: String, val avatar: Int, val ready: Boolean, val connected: Boolean)
@Serializable data class WinningTicket(val id: String, val playerId: String, val ordinal: Int)

/** A deliberate allow-list. Never return a domain Round from a network endpoint. */
@Serializable data class PublicRound(
    val id: String,
    val status: RoundStatus,
    val called: List<Int>,
    val ownTickets: List<Ticket>,
    val awards: List<Award>,
    val customAwards: List<CustomAward>,
    val scores: Map<String, Int>,
    val drawCommitment: String,
    val revealedOrder: List<Int>? = null,
    val revealedNonce: String? = null,
    // Defaults allow reading receipts written before native online play was added.
    val players: List<Player> = emptyList(),
    val winningTickets: List<WinningTicket> = emptyList(),
    val ticketCounts: Map<String, Int> = emptyMap(),
    @EncodeDefault(EncodeDefault.Mode.NEVER) val powers: MatchPowers? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val winnings: Map<String, RoundWinnings> = emptyMap(),
)
@Serializable data class RoomEvent(val revision: Long, val type: String, val at: Long, val roundId: String? = null)
@Serializable data class RoomView(
    val protocolVersion: Int = PROTOCOL_VERSION,
    val code: String,
    val roomId: String,
    val revision: Long,
    val phase: RoomPhase,
    val hostId: String,
    val locked: Boolean,
    val options: RoomOptions,
    val members: List<MemberView>,
    val round: PublicRound?,
    val nextDrawAt: Long?,
    val expiresAt: Long,
    val serverTime: Long,
    val coins: CoinTableView? = null,
    val wallet: WalletView? = null,
)
@Serializable data class RoomUpdate(val snapshot: RoomView, val events: List<RoomEvent>, val resyncRequired: Boolean)
@Serializable data class EventAck(val revision: Long)

@Serializable data class CommandRequest(val id: String, val expectedRevision: Long, val action: RoomAction)
@Serializable sealed class RoomAction {
    @Serializable @SerialName("react") data class React(val roundId: String, val reaction: FriendReaction) : RoomAction() {
        init { require(roundId.length in 1..64) }
    }
    @Serializable @SerialName("mark") data class Mark(val roundId: String, val ticketId: String, val number: Int) : RoomAction() {
        init { require(roundId.length in 1..64 && ticketId.length in 1..100 && number in 1..90) }
    }
    @Serializable @SerialName("use_power") data class UsePower(val roundId: String, val ticketId: String, val power: MatchPower) : RoomAction() {
        init { require(roundId.length in 1..64 && ticketId.length in 1..100) }
    }
    @Serializable @SerialName("buy_tickets") data class BuyTickets(val quantity: Int) : RoomAction() {
        init { require(quantity in 1..6) }
    }
    @Serializable @SerialName("ready") data class Ready(val value: Boolean) : RoomAction()
    @Serializable @SerialName("avatar") data class ChooseAvatar(val avatar: Int) : RoomAction()
    @Serializable @SerialName("configure") data class Configure(val options: RoomOptions) : RoomAction()
    @Serializable @SerialName("lock") data class Lock(val value: Boolean) : RoomAction()
    @Serializable @SerialName("start") data object Start : RoomAction()
    @Serializable @SerialName("draw") data object Draw : RoomAction()
    @Serializable @SerialName("claim") data class Claim(val roundId: String, val drawIndex: Int, val markedNumbers: Set<Int>, val selection: ClaimSelection) : RoomAction() {
        init { require(roundId.length in 1..64 && drawIndex in 1..90 && markedNumbers.size <= 90 && markedNumbers.all { it in 1..90 }) }
    }
    @Serializable @SerialName("pause") data object Pause : RoomAction()
    @Serializable @SerialName("resume") data object Resume : RoomAction()
    @Serializable @SerialName("end") data object End : RoomAction()
    @Serializable @SerialName("rematch") data object Rematch : RoomAction()
    @Serializable @SerialName("remove") data class Remove(val playerId: String) : RoomAction()
    @Serializable @SerialName("leave") data object Leave : RoomAction()
}
