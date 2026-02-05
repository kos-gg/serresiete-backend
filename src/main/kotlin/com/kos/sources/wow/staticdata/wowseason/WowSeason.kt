package com.kos.sources.wow.staticdata.wowseason

import kotlinx.serialization.Serializable

@Serializable
data class WowSeason(
    val id: Int,
    val name: String,
    val expansionId: Int,
    val seasonData: String,
    val isCurrentSeason: Boolean
) {
    fun same(other: WowSeason): Boolean =
        this.id == other.id && this.expansionId == other.expansionId

}