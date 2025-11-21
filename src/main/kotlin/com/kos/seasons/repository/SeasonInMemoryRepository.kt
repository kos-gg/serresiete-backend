package com.kos.seasons.repository

import arrow.core.Either
import com.kos.common.InMemoryRepository
import com.kos.common.InsertError
import com.kos.seasons.GameSeason
import com.kos.seasons.WowSeason

class SeasonInMemoryRepository : SeasonRepository, InMemoryRepository {

    private val wowSeasons: MutableList<WowSeason> = mutableListOf()

    override suspend fun insert(season: GameSeason): Either<InsertError, Boolean> {
        when (season) {
            is WowSeason -> {
                if (this.wowSeasons.any { wowSeason -> season.same(wowSeason) })
                    return Either.Left(InsertError("Error inserting wow season $season because it already exists."))
                else {
                    this.wowSeasons.add(season)
                    return Either.Right(true)
                }
            }
        }
    }

    override suspend fun state(): SeasonsState {
        return SeasonsState(wowSeasons)
    }

    override suspend fun withState(initialState: SeasonsState): SeasonRepository {
        wowSeasons.addAll(initialState.wowSeasons)
        return this
    }

    override fun clear() {
        wowSeasons.clear()
    }
}