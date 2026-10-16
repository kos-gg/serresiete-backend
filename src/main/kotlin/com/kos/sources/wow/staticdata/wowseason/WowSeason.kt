package com.kos.sources.wow.staticdata.wowseason

import kotlinx.serialization.SerialName
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
    @SerialName("is_main_season")
    val isCurrentSeason: Boolean,
    val name: String,
    @SerialName("slug")
    val slug: String,
    @SerialName("blizzard_season_id")
    val blizzardSeasonId: Int,
    val dungeons: List<Dungeon>
)

@Serializable
data class Dungeon(
    val name: String,
    @SerialName("short_name")
    val shortName: String,
    @SerialName("challenge_mode_id")
    val dungeonId: Int,
    @SerialName("icon_url")
    val icon: String,
)
