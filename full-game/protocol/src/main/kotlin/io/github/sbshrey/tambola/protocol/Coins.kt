@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package io.github.sbshrey.tambola.protocol

import io.github.sbshrey.tambola.domain.COIN_TICKET_PRICE
import io.github.sbshrey.tambola.domain.CoinPrize
import io.github.sbshrey.tambola.domain.PowerUp
import kotlinx.serialization.Serializable
import kotlinx.serialization.EncodeDefault

/** Spendable balance only; pending same-call prize shares are excluded. */
@Serializable data class WalletView(
    val balance: Long,
    val revision: Long,
    val refillAfter: Long,
    val ticketPrice: Long = COIN_TICKET_PRICE,
)
@Serializable data class RefillRequest(val id: String)
/** Returned by the upgraded client's daily login; UTC eligibility is server-owned. */
@Serializable data class LoginRewards(val wallet: WalletView, val day: Int, val coins: Long, val nextAt: Long,
    val betaBonus: Long, val newlyCollected: Boolean)
@Serializable data class MatchRequest(
    val id: String, val tickets: Int,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val friendTable: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val friendCode: String? = null,
    /** Explicit consent to purchase into the successor of this completed friends round. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val previousFriendRound: String? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val rulesVersion: Int = 1,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val powerUp: PowerUp = PowerUp.NONE,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val powersEnabled: Boolean = false,
    /** Capability opt-in: older apps cannot decode more than five computer seats. */
    @EncodeDefault(EncodeDefault.Mode.NEVER) val largeMatch: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val previewPowers: Boolean = false,
) {
    init {
        require(tickets in 1..6)
        require(rulesVersion in 1..2)
        require(powerUp == PowerUp.NONE || rulesVersion == 2)
        require(!powersEnabled || (rulesVersion == 2 && powerUp == PowerUp.NONE))
        require(!largeMatch || (rulesVersion == 2 && !friendTable))
        require(!previewPowers || powersEnabled)
        require(friendCode == null || (friendTable && friendCode.matches(Regex("[A-HJ-NP-Z2-9]{8}"))))
        require(previousFriendRound == null || (friendTable && friendCode != null && previousFriendRound.isNotBlank()))
    }
}
@Serializable data class CoinTableView(
    val tickets: Int,
    val pool: Long,
    val prizes: List<CoinPrize>,
    val ownTickets: Int,
    val startsAt: Long?,
    val settledWinnings: Long = 0,
    val returnedCoins: Long = 0,
    // Omitted for quick play so already installed protocol-v4 apps keep decoding it.
    @EncodeDefault(EncodeDefault.Mode.NEVER) val friendTable: Boolean = false,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val powerUp: PowerUp = PowerUp.NONE,
    @EncodeDefault(EncodeDefault.Mode.NEVER) val bonusCoins: Long = 0,
)
