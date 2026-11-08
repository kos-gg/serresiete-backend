package com.kos.sources.wowforever.itemmedia

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class WowForeverItemMediaDatabaseRepository(private val db: Database) : WowForeverItemMediaRepository {

    private object WowForeverItemMediaTable : Table("wow_forever_item_media") {
        val itemId = long("item_id")
        val icon = text("icon")

        override val primaryKey = PrimaryKey(itemId)
    }

    override suspend fun getIcons(itemIds: Set<Long>): Map<Long, String> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverItemMediaTable.selectAll()
                .where { WowForeverItemMediaTable.itemId inList itemIds }
                .associate { it[WowForeverItemMediaTable.itemId] to it[WowForeverItemMediaTable.icon] }
        }

    override suspend fun insert(icons: List<WowForeverItemMedia>) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverItemMediaTable.batchInsert(icons, ignore = true) {
                this[WowForeverItemMediaTable.itemId] = it.itemId
                this[WowForeverItemMediaTable.icon] = it.icon
            }
        }
    }

    override suspend fun state(): List<WowForeverItemMedia> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverItemMediaTable.selectAll()
                .orderBy(WowForeverItemMediaTable.itemId to SortOrder.ASC)
                .map { resultRowToItemMedia(it) }
        }

    override suspend fun withState(initialState: List<WowForeverItemMedia>): WowForeverItemMediaRepository {
        insert(initialState)
        return this
    }

    private fun resultRowToItemMedia(row: ResultRow) =
        WowForeverItemMedia(row[WowForeverItemMediaTable.itemId], row[WowForeverItemMediaTable.icon])
}
