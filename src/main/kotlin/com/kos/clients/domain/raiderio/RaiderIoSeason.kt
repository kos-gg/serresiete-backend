package com.kos.clients.domain.raiderio

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
    val dungeonId: Int
)

@Serializable
data class ExpansionSeasons(
    @Serializable
    val seasons: List<Season>
)
