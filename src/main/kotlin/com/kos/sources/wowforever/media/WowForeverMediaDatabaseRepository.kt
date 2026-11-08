package com.kos.sources.wowforever.media

import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.Op
import org.jetbrains.exposed.sql.ResultRow
import org.jetbrains.exposed.sql.SortOrder
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greaterEq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.plus
import org.jetbrains.exposed.sql.Table
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchInsert
import org.jetbrains.exposed.sql.selectAll
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import org.jetbrains.exposed.sql.update

class WowForeverMediaDatabaseRepository(private val db: Database) : WowForeverMediaRepository {

    private object WowForeverMediaTable : Table("wow_forever_media") {
        val kind = text("kind")
        val id = long("id")
        val region = text("region")
        val icon = text("icon").nullable()
        val status = text("status")
        val attempts = integer("attempts")

        override val primaryKey = PrimaryKey(kind, id)
    }

    override suspend fun getIcons(kind: WowForeverMediaKind, ids: Set<Long>): Map<Long, String> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverMediaTable.selectAll()
                .where {
                    (WowForeverMediaTable.kind eq kind.name) and
                            (WowForeverMediaTable.id inList ids) and
                            (WowForeverMediaTable.status eq WowForeverMediaStatus.RESOLVED.name)
                }
                .mapNotNull { row -> row[WowForeverMediaTable.icon]?.let { row[WowForeverMediaTable.id] to it } }
                .toMap()
        }

    override suspend fun registerPending(kind: WowForeverMediaKind, region: String, ids: Set<Long>) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverMediaTable.batchInsert(ids, ignore = true) { id ->
                this[WowForeverMediaTable.kind] = kind.name
                this[WowForeverMediaTable.id] = id
                this[WowForeverMediaTable.region] = region
                this[WowForeverMediaTable.icon] = null
                this[WowForeverMediaTable.status] = WowForeverMediaStatus.PENDING.name
                this[WowForeverMediaTable.attempts] = 0
            }
        }
    }

    override suspend fun pending(limit: Int): List<WowForeverPendingMedia> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverMediaTable.selectAll()
                .where { WowForeverMediaTable.status eq WowForeverMediaStatus.PENDING.name }
                .orderBy(WowForeverMediaTable.kind to SortOrder.ASC, WowForeverMediaTable.id to SortOrder.ASC)
                .limit(limit)
                .map {
                    WowForeverPendingMedia(
                        WowForeverMediaKind.valueOf(it[WowForeverMediaTable.kind]),
                        it[WowForeverMediaTable.id],
                        it[WowForeverMediaTable.region]
                    )
                }
        }

    override suspend fun resolve(kind: WowForeverMediaKind, id: Long, icon: String) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverMediaTable.update({ pendingRow(kind, id) }) {
                it[WowForeverMediaTable.icon] = icon
                it[status] = WowForeverMediaStatus.RESOLVED.name
            }
        }
    }

    override suspend fun markUnavailable(kind: WowForeverMediaKind, id: Long) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverMediaTable.update({ pendingRow(kind, id) }) {
                it[status] = WowForeverMediaStatus.UNAVAILABLE.name
            }
        }
    }

    override suspend fun markFailed(kind: WowForeverMediaKind, id: Long, maxAttempts: Int) {
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverMediaTable.update({ pendingRow(kind, id) }) {
                it[attempts] = attempts + 1
            }
            WowForeverMediaTable.update({ pendingRow(kind, id) and (WowForeverMediaTable.attempts greaterEq maxAttempts) }) {
                it[status] = WowForeverMediaStatus.UNAVAILABLE.name
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
        newSuspendedTransaction(Dispatchers.IO, db) {
            WowForeverMediaTable.batchInsert(initialState) {
                this[WowForeverMediaTable.kind] = it.kind.name
                this[WowForeverMediaTable.id] = it.id
                this[WowForeverMediaTable.region] = it.region
                this[WowForeverMediaTable.icon] = it.icon
                this[WowForeverMediaTable.status] = it.status.name
                this[WowForeverMediaTable.attempts] = it.attempts
            }
        }
        return this
    }

    private fun pendingRow(kind: WowForeverMediaKind, id: Long): Op<Boolean> =
        (WowForeverMediaTable.kind eq kind.name) and
                (WowForeverMediaTable.id eq id) and
                (WowForeverMediaTable.status eq WowForeverMediaStatus.PENDING.name)

    private fun resultRowToMedia(row: ResultRow) = WowForeverMedia(
        WowForeverMediaKind.valueOf(row[WowForeverMediaTable.kind]),
        row[WowForeverMediaTable.id],
        row[WowForeverMediaTable.region],
        row[WowForeverMediaTable.icon],
        WowForeverMediaStatus.valueOf(row[WowForeverMediaTable.status]),
        row[WowForeverMediaTable.attempts]
    )
}
