package com.kos.seasons.repository

import arrow.core.Either
import com.kos.common.InsertError
import com.kos.common.WithState
import com.kos.seasons.GameSeason
import com.kos.seasons.WowSeason

interface SeasonRepository : WithState<SeasonsState, SeasonRepository> {
    suspend fun insert(season: GameSeason): Either<InsertError, Boolean>
    suspend fun get(): List<WowSeason>
}

data class SeasonsState(
    val wowSeasons: List<WowSeason>
)