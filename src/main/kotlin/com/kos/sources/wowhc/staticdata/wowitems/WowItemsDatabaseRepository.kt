package com.kos.sources.wowhc.staticdata.wowitems

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.JsonParseError
import com.kos.clients.domain.GetWowItemResponse
import com.kos.clients.domain.GetWowMediaResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

object WowClassicStaticItems : Table("wow_classic_static_items") {
    val id = long("id")
    val item = text("item")
    val media = text("media")

    override val primaryKey = PrimaryKey(id)
}

class WowItemsDatabaseRepository(private val db: Database) : WowItemsRepository {
    private val json = Json {
        ignoreUnknownKeys = true
    }

    override suspend fun getItemMedia(
        id: Long
    ): Either<ClientError, GetWowMediaResponse> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val itemString = WowClassicStaticItems.selectAll().where { WowClassicStaticItems.id.eq(id) }
                .map { it[WowClassicStaticItems.media] }.singleOrNull()
            when (itemString) {
                null -> Either.Left(JsonParseError("", "Not found item media"))
                else ->
                    try {
                        Either.Right(json.decodeFromString<GetWowMediaResponse>(itemString))
                    } catch (e: SerializationException) {
                        Either.Left(JsonParseError(itemString, e.stackTraceToString()))
                    }
            }
        }
    }

    override suspend fun getItem(
        id: Long
    ): Either<ClientError, GetWowItemResponse> {
        return newSuspendedTransaction(Dispatchers.IO, db) {
            val itemString = WowClassicStaticItems.selectAll().where { WowClassicStaticItems.id.eq(id) }
                .map { it[WowClassicStaticItems.item] }.singleOrNull()
            when (itemString) {
                null -> Either.Left(JsonParseError("", "Not found item"))
                else ->
                    try {
                        Either.Right(json.decodeFromString<GetWowItemResponse>(itemString))
                    } catch (e: SerializationException) {
                        Either.Left(JsonParseError(itemString, e.stackTraceToString()))
                    }
            }
        }
    }

    override suspend fun state(): WowItemsState {
        return newSuspendedTransaction(Dispatchers.IO) {
            WowItemsState(
                WowClassicStaticItems.selectAll().map {
                    WowItemState(
                        it[WowClassicStaticItems.id],
                        it[WowClassicStaticItems.item],
                        it[WowClassicStaticItems.media]
                    )
                }
            )
        }
    }

    override suspend fun withState(initialState: WowItemsState): WowItemsRepository {
        newSuspendedTransaction(Dispatchers.IO) {
            WowClassicStaticItems.batchInsert(initialState.wowItems) {
                this[WowClassicStaticItems.id] = it.id
                this[WowClassicStaticItems.media] = it.media
                this[WowClassicStaticItems.item] = it.item
            }
        }
        return this
    }
}