package com.kos.sources.wowhc.staticdata.wowitems

import arrow.core.Either
import com.kos.clients.domain.GetWowItemResponse
import com.kos.clients.domain.GetWowMediaResponse
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

class WowItemsDatabaseRepository(private val db: Database) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun getItemMedia(
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
                        val error = json.decodeFromString<RiotError>(itemString)
                        Either.Left(error)
                    }
            }
        }
    }

    suspend fun getItem(
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
                        val error = json.decodeFromString<RiotError>(itemString)
                        Either.Left(error)
                    }
            }
        }
    }
}