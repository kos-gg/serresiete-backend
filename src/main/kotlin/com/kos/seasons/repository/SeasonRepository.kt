package com.kos.seasons.repository

import arrow.core.Either
import com.kos.common.WithState
import com.kos.common.error.InsertError
import com.kos.seasons.GameSeason
import com.kos.seasons.WowSeason

interface SeasonRepository : WithState<SeasonsState, SeasonRepository> {
    suspend fun insert(season: GameSeason): Either<InsertError, Boolean>
}

data class SeasonsState(
    val wowSeasons: List<WowSeason>
)