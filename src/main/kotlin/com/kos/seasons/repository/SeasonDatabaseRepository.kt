package com.kos.seasons.repository

import arrow.core.Either
import com.kos.common.InsertError
import com.kos.seasons.GameSeason
import com.kos.seasons.WowSeason
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class SeasonDatabaseRepository(private val db: Database) : SeasonRepository {
    object WowSeasons : Table("mythic_plus_seasons") {
        val seasonId = integer("id")
        val name = text("name")
        val expansionId = integer("expansion_id")
        val data = text("data")

        override val primaryKey = PrimaryKey(seasonId, name)
    }

    override suspend fun insert(season: GameSeason): Either<InsertError, Boolean> {
        return Either.catch {
            newSuspendedTransaction(Dispatchers.IO, db) {
                when (season) {
                    is WowSeason -> {
                        WowSeasons.insert {
                            it[seasonId] = season.id
                            it[name] = season.name
                            it[expansionId] = season.expansionId
                            it[data] = season.seasonData
                        }
                    }
                }
                true
            }
        }.mapLeft { InsertError(it.message ?: it.stackTraceToString()) }
    }

    override suspend fun state(): SeasonsState {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            SeasonsState(
                WowSeasons.selectAll().map { resultRowToWowSeason(it) }
            )
        }
    }

    override suspend fun withState(initialState: SeasonsState): SeasonRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowSeasons.batchInsert(initialState.wowSeasons) {
                this[WowSeasons.seasonId] = it.id
                this[WowSeasons.name] = it.name
                this[WowSeasons.expansionId] = it.expansionId
                this[WowSeasons.data] = it.seasonData
            }
        }

        return this
    }

    private fun resultRowToWowSeason(row: ResultRow) = WowSeason(
        row[WowSeasons.seasonId],
        row[WowSeasons.name],
        row[WowSeasons.expansionId],
        row[WowSeasons.data]
    )
}