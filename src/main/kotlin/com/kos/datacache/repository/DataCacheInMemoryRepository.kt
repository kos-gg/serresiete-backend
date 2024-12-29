package com.kos.datacache.repository

import com.kos.common.InMemoryRepository
import com.kos.common.collect
import com.kos.datacache.DataCache
import com.kos.views.Game
import java.time.OffsetDateTime

class DataCacheInMemoryRepository : DataCacheRepository, InMemoryRepository {
    private val cachedData: MutableList<DataCache> = mutableListOf()

    override suspend fun insert(data: List<DataCache>): Boolean = cachedData.addAll(data)

    override suspend fun get(characterId: Long): List<DataCache> = cachedData.filter { it.characterId == characterId }
    override suspend fun deleteExpiredRecord(ttl: Long, game: Game?, clearAll: Boolean): Int {
        val currentTime = OffsetDateTime.now()
        return if (clearAll) {
            val deletedRecords = cachedData.count { it.inserted.plusHours(ttl) < currentTime && it.game == game }
            cachedData.removeAll { it.inserted.plusHours(ttl) < currentTime && it.game == game }
            deletedRecords
        } else {
            val idsToRetain = cachedData
                .filter { it.inserted.plusHours(ttl) < currentTime && it.game == game }
                .groupBy { it.characterId }
                .map {
                    it.key to it.value.size
                }.collect(
                    filter = { it.second == 1 },
                    map = { it.first }
                )

            val deletedRecords = cachedData.count { it.inserted.plusHours(ttl) < currentTime && it.game == game }
            cachedData.removeAll { it.inserted.plusHours(ttl) < currentTime && it.game == game && !idsToRetain.contains(it.characterId) }
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