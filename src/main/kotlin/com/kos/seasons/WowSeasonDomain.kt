package com.kos.seasons

import kotlinx.serialization.Serializable

@Serializable
data class WowSeason(
    override val id: Int,
    override val name: String,
    val expansionId: Int,
    val seasonData: String,
) : GameSeason {
    override fun same(other: GameSeason): Boolean {
        return when (other) {
            is WowSeason -> this.id == other.id && this.expansionId == this.expansionId
            else -> false
        }
    }
}