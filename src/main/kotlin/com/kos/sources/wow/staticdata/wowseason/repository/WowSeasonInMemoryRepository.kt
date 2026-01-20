package com.kos.sources.wow.staticdata.wowseason.repository

import arrow.core.Either
import com.kos.common.InMemoryRepository
import com.kos.common.error.InsertError
import com.kos.sources.wow.staticdata.wowseason.WowSeason

class WowSeasonInMemoryRepository : WowSeasonRepository, InMemoryRepository {

    private val wowSeasons: MutableList<WowSeason> = mutableListOf()

    override suspend fun insert(season: WowSeason): Either<InsertError, Boolean> {
        return if (this.wowSeasons.any { wowSeason -> season.same(wowSeason) })
            Either.Left(InsertError("Error inserting wow season $season because it already exists."))
        else {
            this.wowSeasons.add(season)
            Either.Right(true)
        }
    }

    override suspend fun state(): WowSeasonsState {
        return WowSeasonsState(wowSeasons)
    }

    override suspend fun withState(initialState: WowSeasonsState): WowSeasonRepository {
        wowSeasons.addAll(initialState.wowSeasons)
        return this
    }

    override fun clear() {
        wowSeasons.clear()
    }
}