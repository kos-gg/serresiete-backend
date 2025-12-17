package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.GetWowItemResponse
import com.kos.clients.domain.GetWowMediaResponse
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

class BlizzardDatabaseClient(private val db: Database) {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun getItemMedia(
        id: Long
    ): Either<ClientError, GetWowMediaResponse> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val itemString = WowClassicStaticItems.selectAll().where { WowClassicStaticItems.id.eq(id) }
                .map { it[WowClassicStaticItems.media] }.singleOrNull()
            when (itemString) {
                null -> Either.Left(com.kos.clients.JsonParseError("", ""))
                else ->
                    try {
                        Either.Right(json.decodeFromString<GetWowMediaResponse>(itemString))
                    } catch (e: SerializationException) {
                        Either.Left(com.kos.clients.JsonParseError(itemString, e.stackTraceToString()))
                    }
            }
        }
    }

    suspend fun getItem(
        id: Long
    ): Either<ClientError, GetWowItemResponse> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val itemString = WowClassicStaticItems.selectAll().where { WowClassicStaticItems.id.eq(id) }
                .map { it[WowClassicStaticItems.item] }.singleOrNull()
            when (itemString) {
                null -> Either.Left(com.kos.clients.JsonParseError("", ""))
                else ->
                    try {
                        Either.Right(json.decodeFromString<GetWowItemResponse>(itemString))
                    } catch (e: SerializationException) {
                        Either.Left(com.kos.clients.JsonParseError(itemString, e.stackTraceToString()))
                    }
            }
        }
    }
}