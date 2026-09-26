package io.github.sbshrey.tambola.protocol

import io.github.sbshrey.tambola.domain.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

const val PROTOCOL_VERSION = 3
val WireJson = Json { encodeDefaults = true }

@Serializable data class GuestRequest(val displayName: String, val avatar: Int = 0)
@Serializable data class GuestCredentials(val playerId: String, val token: String, val expiresAt: Long)
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
) {
    init {
        require(game.mode == GameMode.ONLINE && capacity in 2..32 && intervalSeconds in 5..30)
        require(computerPlayers in 0..5 && computerPlayers < capacity)
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
)
@Serializable data class RoomUpdate(val snapshot: RoomView, val events: List<RoomEvent>, val resyncRequired: Boolean)
@Serializable data class EventAck(val revision: Long)

@Serializable data class CommandRequest(val id: String, val expectedRevision: Long, val action: RoomAction)
@Serializable sealed class RoomAction {
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
