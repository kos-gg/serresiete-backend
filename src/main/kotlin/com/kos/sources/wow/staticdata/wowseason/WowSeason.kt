package com.kos.sources.wow.staticdata.wowseason

import kotlinx.serialization.Serializable

@Serializable
data class WowSeason(
    val id: Int,
    val name: String,
    val slug: String,
    val expansionId: Int,
    val seasonData: String,
    val isCurrentSeason: Boolean
) {
    fun same(other: WowSeason): Boolean =
        this.id == other.id && this.expansionId == other.expansionId

}

@Serializable
data class Season(
    val isCurrentSeason: Boolean,
    val name: String,
    val slug: String,
    val blizzardSeasonId: Int,
    val dungeons: List<Dungeon>
)

@Serializable
data class Dungeon(
    val name: String,
    val shortName: String,
    val dungeonId: Int,
    val icon: String,
)
