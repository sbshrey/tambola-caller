package io.github.sbshrey.tambola.protocol

import kotlinx.serialization.Serializable

const val AD_REWARD_COINS = 1_000L
const val AD_REWARDS_PER_DAY = 5

@Serializable data class RewardAdIntent(val id: String, val adUnit: String, val coins: Long, val expiresAt: Long)
@Serializable data class RewardAdStatus(val confirmed: Boolean, val wallet: WalletView)
