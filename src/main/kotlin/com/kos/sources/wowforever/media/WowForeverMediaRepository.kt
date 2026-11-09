package com.kos.sources.wowforever.media

import com.kos.common.WithState

enum class WowForeverMediaKind { ITEM, SPELL }

data class WowForeverMedia(
    val kind: WowForeverMediaKind,
    val id: Long,
    val icon: String
)

interface WowForeverMediaRepository : WithState<List<WowForeverMedia>, WowForeverMediaRepository> {
    suspend fun getIcons(kind: WowForeverMediaKind, ids: Set<Long>): Map<Long, String>
    suspend fun insert(media: List<WowForeverMedia>)
}
