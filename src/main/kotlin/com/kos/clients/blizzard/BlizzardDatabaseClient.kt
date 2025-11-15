package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.domain.GetWowCharacterResponse
import com.kos.clients.domain.GetWowCharacterStatsResponse
import com.kos.clients.domain.GetWowEquipmentResponse
import com.kos.clients.domain.GetWowItemResponse
import com.kos.clients.domain.GetWowMediaResponse
import com.kos.clients.domain.GetWowRealmResponse
import com.kos.clients.domain.GetWowSpecializationsResponse
import com.kos.clients.domain.RiotError
import com.kos.common.HttpError
import com.kos.common.JsonParseError
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object WowClassicStaticItems : Table("wow_classic_static_items") {
    val id = long("id")
    val item = text("item")
    val media = text("media")

    override val primaryKey = PrimaryKey(id)
}

class BlizzardDatabaseClient(private val db: Database) : BlizzardClient {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun getCharacterProfile(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowCharacterResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun getCharacterMedia(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowMediaResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun getCharacterEquipment(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowEquipmentResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun getCharacterSpecializations(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowSpecializationsResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun getCharacterStats(
        region: String,
        realm: String,
        character: String
    ): Either<HttpError, GetWowCharacterStatsResponse> {
        TODO("Not yet implemented")
    }

    override suspend fun getItemMedia(
        region: String,
        id: Long
    ): Either<HttpError, GetWowMediaResponse> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val itemString = WowClassicStaticItems.selectAll().where { WowClassicStaticItems.id.eq(id) }
                .map { it[WowClassicStaticItems.media] }.singleOrNull()
            when (itemString) {
                null -> Either.Left(JsonParseError("", ""))
                else ->
                    try {
                        Either.Right(json.decodeFromString<GetWowMediaResponse>(itemString))
                    } catch (e: SerializationException) {
                        Either.Left(JsonParseError(itemString, e.stackTraceToString()))
                    } catch (e: IllegalArgumentException) {
                        println(e.stackTraceToString())
                        val error = json.decodeFromString<RiotError>(itemString)
                        Either.Left(error)
                    }
            }
        }
    }

    override suspend fun getItem(
        region: String,
        id: Long
    ): Either<HttpError, GetWowItemResponse> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val itemString = WowClassicStaticItems.selectAll().where { WowClassicStaticItems.id.eq(id) }
                .map { it[WowClassicStaticItems.item] }.singleOrNull()
            when (itemString) {
                null -> Either.Left(JsonParseError("", ""))
                else ->
                    try {
                        Either.Right(json.decodeFromString<GetWowItemResponse>(itemString))
                    } catch (e: SerializationException) {
                        Either.Left(JsonParseError(itemString, e.stackTraceToString()))
                    } catch (e: IllegalArgumentException) {
                        println("PROBLEM PARSING ITEM $id ${e.stackTraceToString()}")
                        val error = json.decodeFromString<RiotError>(itemString)
                        Either.Left(error)
                    }
            }
        }
    }

    override suspend fun getRealm(
        region: String,
        id: Long
    ): Either<HttpError, GetWowRealmResponse> {
        TODO("Not yet implemented")
    }
}