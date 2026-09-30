package io.github.sbshrey.tambola.protocol

import io.github.sbshrey.tambola.domain.*
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable enum class GameVariant { TAMBOLA_90, BINGO_75 }

/** New routes and receipts are variant-specific; legacy room routes remain Tambola-only. */
@Serializable data class BingoMatchRequest(
    val id: String, val cards: Int, val friendTable: Boolean = false, val friendCode: String? = null,
    val variant: GameVariant = GameVariant.BINGO_75,
) {
    init {
        require(variant == GameVariant.BINGO_75 && cards in 1..6)
        require(friendCode == null || (friendTable && friendCode.matches(Regex("B-[A-Z2-9]{8}"))))
    }
}

@Serializable data class BingoCommandRequest(val id: String, val expectedRevision: Long, val action: BingoAction) {
    init { require(expectedRevision >= 0) }
}
@Serializable sealed class BingoAction {
    @Serializable @SerialName("bingo_mark") data class Mark(val roundId: String, val cardId: String, val number: Int) : BingoAction() {
        init { require(roundId.length in 1..64 && cardId.length in 1..100 && number in 1..75) }
    }
    @Serializable @SerialName("bingo_claim") data class Claim(val roundId: String, val cardId: String, val pattern: BingoPattern) : BingoAction() {
        init { require(roundId.length in 1..64 && cardId.length in 1..100) }
    }
    @Serializable @SerialName("bingo_start") data object Start : BingoAction()
    @Serializable @SerialName("bingo_leave") data object Leave : BingoAction()
}

/** Never include the server's BingoRound: only the actor's cards/marks and revealed calls. */
@Serializable data class PublicBingoRound(
    val id: String, val status: RoundStatus, val called: List<Int>, val ownCards: List<BingoCard>,
    val ownMarks: Map<String, Set<Int>>, val claims: List<BingoClaim>, val players: List<Player>,
    val cardCounts: Map<String, Int>, val winnersPerPattern: Int, val prizes: List<BingoCoinPrize>,
    val drawCommitment: String, val revealedOrder: List<Int>? = null, val revealedNonce: String? = null,
    val winnings: Map<String, RoundWinnings> = emptyMap(),
)
@Serializable data class BingoRoomView(
    val roomId: String, val code: String, val revision: Long, val phase: RoomPhase,
    val hostId: String, val friendTable: Boolean, val members: List<MemberView>,
    val players: List<Player>, val cardCounts: Map<String, Int>, val startsAt: Long?,
    val nextDrawAt: Long?, val expiresAt: Long, val serverTime: Long, val pool: Long,
    val round: PublicBingoRound?, val wallet: WalletView? = null,
    val variant: GameVariant = GameVariant.BINGO_75,
) { init { require(variant == GameVariant.BINGO_75 && code.matches(Regex("B-[A-Z2-9]{8}"))) } }
