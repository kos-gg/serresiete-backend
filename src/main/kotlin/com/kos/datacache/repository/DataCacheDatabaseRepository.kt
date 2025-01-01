package com.kos.datacache.repository

import com.kos.common.getOrThrow
import com.kos.datacache.DataCache
import com.kos.views.Game
import kotlinx.coroutines.Dispatchers
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.SqlExpressionBuilder.notInList
import org.jetbrains.exposed.sql.transactions.experimental.newSuspendedTransaction
import java.time.OffsetDateTime

class DataCacheDatabaseRepository(private val db: Database) : DataCacheRepository {

    override suspend fun withState(initialState: List<DataCache>): DataCacheDatabaseRepository {
        newSuspendedTransaction(Dispatchers.IO, db) {
            DataCaches.batchInsert(initialState) {
                this[DataCaches.characterId] = it.characterId
                this[DataCaches.data] = it.data
                this[DataCaches.inserted] = it.inserted.toString()
                this[DataCaches.game] = it.game.toString()
            }
        }
        return this
    }

    object DataCaches : Table("data_cache") {
        val characterId = long("character_id")
        val data = text("data")
        val inserted = text("inserted")
        val game = text("game")

        override val primaryKey = PrimaryKey(characterId)
    }

    private fun resultRowToDataCache(row: ResultRow) = DataCache(
        row[DataCaches.characterId],
        row[DataCaches.data],
        OffsetDateTime.parse(row[DataCaches.inserted]),
        Game.fromString(row[DataCaches.game]).getOrThrow()
    )

    override suspend fun insert(data: List<DataCache>): Boolean {
        newSuspendedTransaction(Dispatchers.IO, db) {
            DataCaches.batchInsert(data) {
                this[DataCaches.characterId] = it.characterId
                this[DataCaches.data] = it.data
                this[DataCaches.inserted] = it.inserted.toString()
                this[DataCaches.game] = it.game.toString()
            }
        }
        return true
    }

    override suspend fun get(characterId: Long): List<DataCache> =
        newSuspendedTransaction(Dispatchers.IO, db) {
            DataCaches.selectAll().where { DataCaches.characterId.eq(characterId) }.map { resultRowToDataCache(it) }
        }

    override suspend fun deleteExpiredRecord(ttl: Long, game: Game?, keepLastRecord: Boolean): Int {
        val gameCondition = game?.let { DataCaches.game eq it.toString() }
        val expiredCondition = DataCaches.inserted.less(OffsetDateTime.now().minusHours(ttl).toString())

        val where =
            Op.TRUE
                .andIfNotNull(gameCondition)
                .and(expiredCondition)

        return if (keepLastRecord) newSuspendedTransaction(Dispatchers.IO, db) {
            val idsToRetain = DataCaches.select(DataCaches.characterId)
                .where(where)
                .groupBy(DataCaches.characterId)
                .having { DataCaches.characterId.count() eq 1 }
                .map { it[DataCaches.characterId] }

            DataCaches.deleteWhere {
                where.and(characterId notInList idsToRetain)
            }

        } else {
            newSuspendedTransaction(Dispatchers.IO, db) {
                DataCaches.deleteWhere { where }
            }
        }
    }

    override suspend fun state(): List<DataCache> =
        newSuspendedTransaction(Dispatchers.IO, db) { DataCaches.selectAll().map { resultRowToDataCache(it) } }
}