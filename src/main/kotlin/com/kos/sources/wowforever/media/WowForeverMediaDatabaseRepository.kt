package com.kos.sources.wowforever.media

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction

class WowForeverMediaDatabaseRepository(private val db: Database) : WowForeverMediaRepository {

    private object WowForeverMediaTable : Table("wow_forever_media") {
        val kind = text("kind")
        val id = long("id")
        val icon = text("icon")

        override val primaryKey = PrimaryKey(kind, id)
    }

    override suspend fun getIcons(kind: WowForeverMediaKind, ids: Set<Long>): Map<Long, String> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverMediaTable.selectAll()
                .where { (WowForeverMediaTable.kind eq kind.name) and (WowForeverMediaTable.id inList ids) }
                .associate { it[WowForeverMediaTable.id] to it[WowForeverMediaTable.icon] }
        }

    override suspend fun insert(media: List<WowForeverMedia>) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverMediaTable.batchInsert(media, ignore = true) {
                this[WowForeverMediaTable.kind] = it.kind.name
                this[WowForeverMediaTable.id] = it.id
                this[WowForeverMediaTable.icon] = it.icon
            }
        }
    }

    override suspend fun state(): List<WowForeverMedia> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverMediaTable.selectAll()
                .orderBy(WowForeverMediaTable.kind to SortOrder.ASC, WowForeverMediaTable.id to SortOrder.ASC)
                .map { resultRowToMedia(it) }
        }

    override suspend fun withState(initialState: List<WowForeverMedia>): WowForeverMediaRepository {
        insert(initialState)
        return this
    }

    private fun resultRowToMedia(row: ResultRow) = WowForeverMedia(
        WowForeverMediaKind.valueOf(row[WowForeverMediaTable.kind]),
        row[WowForeverMediaTable.id],
        row[WowForeverMediaTable.icon]
    )
}
