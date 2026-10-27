package com.kos.sources.wow.staticdata.wowseason.repository

import com.kos.common.WithState
import com.kos.sources.wow.staticdata.wowseason.WowSeason

interface WowSeasonRepository : WithState<WowSeasonsState, WowSeasonRepository> {
    suspend fun insert(season: WowSeason): Boolean
    suspend fun getCurrentSeason(): WowSeason?
}

data class WowSeasonsState(
    val wowSeasons: List<WowSeason>
)