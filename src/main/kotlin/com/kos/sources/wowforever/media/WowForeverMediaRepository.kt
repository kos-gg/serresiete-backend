package com.kos.sources.wowforever.media

import com.kos.common.WithState

enum class WowForeverMediaKind { ITEM, SPELL }

enum class WowForeverMediaStatus { PENDING, RESOLVED, UNAVAILABLE }

data class WowForeverMedia(
    val kind: WowForeverMediaKind,
    val id: Long,
    val region: String,
    val icon: String?,
    val status: WowForeverMediaStatus,
    val attempts: Int
)

data class WowForeverPendingMedia(
    val kind: WowForeverMediaKind,
    val id: Long,
    val region: String
)

interface WowForeverMediaRepository : WithState<List<WowForeverMedia>, WowForeverMediaRepository> {
    suspend fun getIcons(kind: WowForeverMediaKind, ids: Set<Long>): Map<Long, String>
    suspend fun registerPending(kind: WowForeverMediaKind, region: String, ids: Set<Long>)
    suspend fun pending(limit: Int): List<WowForeverPendingMedia>
    suspend fun resolve(kind: WowForeverMediaKind, id: Long, icon: String)
    suspend fun markUnavailable(kind: WowForeverMediaKind, id: Long)
    suspend fun markFailed(kind: WowForeverMediaKind, id: Long, maxAttempts: Int)
}
