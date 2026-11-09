package com.kos.sources.wowforever.media

import com.kos.common.InMemoryRepository

class WowForeverMediaInMemoryRepository : WowForeverMediaRepository, InMemoryRepository {

    private data class Key(val kind: WowForeverMediaKind, val id: Long)

    private val media: MutableMap<Key, WowForeverMedia> = mutableMapOf()

    override suspend fun getIcons(kind: WowForeverMediaKind, ids: Set<Long>): Map<Long, String> =
        media.values
            .filter { it.kind == kind && it.id in ids }
            .associate { it.id to it.icon }

    override suspend fun insert(media: List<WowForeverMedia>) {
        media.forEach { this.media.putIfAbsent(Key(it.kind, it.id), it) }
    }

    override suspend fun state(): List<WowForeverMedia> =
        media.values.sortedWith(compareBy({ it.kind }, { it.id }))

    override suspend fun withState(initialState: List<WowForeverMedia>): WowForeverMediaRepository {
        insert(initialState)
        return this
    }

    override fun clear() {
        media.clear()
    }
}
