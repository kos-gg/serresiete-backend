package com.kos.seasons.repository

import arrow.core.Either
import com.kos.clients.domain.Data
import com.kos.clients.domain.Season
import com.kos.common.InsertError
import com.kos.seasons.GameSeason
import com.kos.seasons.WowSeason
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class SeasonDatabaseRepository(private val db: Database) : SeasonRepository {
    private val json = Json {
        serializersModule = SerializersModule {
            polymorphic(Data::class) {
                subclass(Season::class, Season.serializer())
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    object WowSeasons : Table("mythic_plus_seasons") {
        val seasonId = integer("id")
        val name = text("name")
        val expansionId = integer("expansion_id")
        val data = text("data")

        override val primaryKey = PrimaryKey(seasonId, expansionId)
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
                this[WowSeasons.data] = json.encodeToString(it.seasonData)
            }
        }

        return this
    }

    private fun resultRowToWowSeason(row: ResultRow) = WowSeason(
        row[WowSeasons.seasonId],
        row[WowSeasons.name],
        row[WowSeasons.expansionId],
        json.decodeFromString(row[WowSeasons.data])
    )
}