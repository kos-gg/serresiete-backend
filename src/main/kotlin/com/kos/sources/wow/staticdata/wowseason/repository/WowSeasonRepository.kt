package com.kos.sources.wow.staticdata.wowseason.repository

import arrow.core.Either
import com.kos.common.WithState
import com.kos.sources.wow.staticdata.wowseason.SeasonAlreadyExists
import com.kos.sources.wow.staticdata.wowseason.WowSeason

interface WowSeasonRepository : WithState<WowSeasonsState, WowSeasonRepository> {
    suspend fun insert(season: WowSeason): Either<SeasonAlreadyExists, Boolean>
    suspend fun getCurrentSeason(): WowSeason?
}

data class WowSeasonsState(
    val wowSeasons: List<WowSeason>
)