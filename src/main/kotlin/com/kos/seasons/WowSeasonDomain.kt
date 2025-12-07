package com.kos.seasons

import com.kos.clients.domain.Season
import kotlinx.serialization.Serializable

@Serializable
data class WowSeason(
    val id: Int,
    val name: String,
    val expansionId: Int,
    val seasonData: Season,
) {
    fun same(other: WowSeason): Boolean = this.id == other.id && this.expansionId == other.expansionId
}