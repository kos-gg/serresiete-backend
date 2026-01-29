package com.kos.sources.wow.staticdata.wowseason.repository

import arrow.core.Either
import com.kos.common.error.InsertError
import com.kos.sources.wow.staticdata.wowseason.WowSeason
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class WowSeasonDatabaseRepository(private val db: Database) : WowSeasonRepository {
    object WowSeasons : Table("mythic_plus_seasons") {
        val seasonId = integer("id")
        val name = text("name")
        val expansionId = integer("expansion_id")
        val data = text("data")
        val currentSeason = bool("is_current_season")

        override val primaryKey = PrimaryKey(seasonId, expansionId)
    }

    override suspend fun insert(season: WowSeason): Either<InsertError, Boolean> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            Either.catch {
                WowSeasons.update {
                    it[currentSeason] = false
                }
                WowSeasons.insert {
                    it[seasonId] = season.id
                    it[name] = season.name
                    it[expansionId] = season.expansionId
                    it[data] = season.seasonData
                    it[currentSeason] = true
                }
                true
            }.onLeft { rollback() }.mapLeft { InsertError(it.message ?: it.stackTraceToString()) }
        }
    }
    
    override suspend fun getCurrentSeason(): WowSeason? {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            WowSeasons.selectAll().where { WowSeasons.currentSeason eq true }.map { resultRowToWowSeason(it) }.firstOrNull()
        }
    }

    override suspend fun state(): WowSeasonsState {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            WowSeasonsState(
                WowSeasons.selectAll().map { resultRowToWowSeason(it) }
            )
        }
    }

    override suspend fun withState(initialState: WowSeasonsState): WowSeasonRepository {
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