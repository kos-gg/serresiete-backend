package com.kos.sources.wowhc.staticdata.wowitems

import com.kos.clients.blizzard.asset
import com.kos.clients.blizzard.toWowItemDetails
import com.kos.clients.domain.GetWowItemResponse
import com.kos.clients.domain.GetWowMediaResponse
import kotlinx.coroutines.Dispatchers
import kotlinx.serialization.json.Json
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import com.kos.sources.wowhc.WowItemDetails

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

    override suspend fun getItemMedia(id: Long): String? =
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowClassicStaticItems.selectAll().where { WowClassicStaticItems.id.eq(id) }
                .map { it[WowClassicStaticItems.media] }.singleOrNull()
        }?.let { json.decodeFromString<GetWowMediaResponse>(it).asset("icon") }

    override suspend fun getItem(id: Long): WowItemDetails? =
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowClassicStaticItems.selectAll().where { WowClassicStaticItems.id.eq(id) }
                .map { it[WowClassicStaticItems.item] }.singleOrNull()
        }?.let { json.decodeFromString<GetWowItemResponse>(it).toWowItemDetails() }

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