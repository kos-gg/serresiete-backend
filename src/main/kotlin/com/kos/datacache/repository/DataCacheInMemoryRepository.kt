package com.kos.datacache.repository

import com.kos.common.InMemoryRepository
import com.kos.datacache.DataCache
import com.kos.views.Game
import java.time.OffsetDateTime

class DataCacheInMemoryRepository : DataCacheRepository, InMemoryRepository {
    private val cachedData: MutableList<DataCache> = mutableListOf()

    override suspend fun insert(data: List<DataCache>): Boolean = cachedData.addAll(data)

    override suspend fun get(characterId: Long): List<DataCache> = cachedData.filter { it.characterId == characterId }
    override suspend fun deleteExpiredRecord(ttl: Long, game: Game?, keepLastRecord: Boolean): Int {
        fun Game?.matches(other: Game): Boolean = this == null || this == other

        val currentTime = OffsetDateTime.now()
        return if (keepLastRecord) {
            val idsToRetain = cachedData
                .filter { it.inserted.plusHours(ttl) < currentTime && game.matches(it.game) }
                .groupBy { it.characterId }
                .filter { it.value.size == 1 }
                .keys

            val deletedRecords = cachedData.count {
                it.inserted.plusHours(ttl) < currentTime
                        && game.matches(it.game)
                        && it.characterId !in idsToRetain
            }
            cachedData.removeAll {
                it.inserted.plusHours(ttl) < currentTime
                        && game.matches(it.game) &&
                        it.characterId !in idsToRetain
            }
            deletedRecords
        } else {
            val deletedRecords = cachedData.count { it.inserted.plusHours(ttl) < currentTime && game.matches(it.game) }
            cachedData.removeAll { it.inserted.plusHours(ttl) < currentTime && game.matches(it.game) }
            deletedRecords
        }
    }

    override suspend fun state(): List<DataCache> = cachedData
    override suspend fun withState(initialState: List<DataCache>): DataCacheInMemoryRepository {
        cachedData.addAll(initialState)
        return this
    }

    override fun clear() {
        cachedData.clear()
    }
}