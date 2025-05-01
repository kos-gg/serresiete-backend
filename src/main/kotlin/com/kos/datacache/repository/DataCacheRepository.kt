package com.kos.datacache.repository

import com.kos.common.WithState
import com.kos.datacache.DataCache
import com.kos.views.Game

interface DataCacheRepository : WithState<List<DataCache>, DataCacheRepository> {
    suspend fun insert(data: List<DataCache>): Boolean
    suspend fun get(entityId: Long): List<DataCache>
    suspend fun deleteExpiredRecord(ttl: Long, game: Game?, keepLastRecord: Boolean): Int
    suspend fun clearRecords(game: Game?): Int
}