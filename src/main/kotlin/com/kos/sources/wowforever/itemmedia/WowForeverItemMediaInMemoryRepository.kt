package com.kos.sources.wowforever.itemmedia

import com.kos.common.InMemoryRepository

class WowForeverItemMediaInMemoryRepository : WowForeverItemMediaRepository, InMemoryRepository {

    private val itemMedia: MutableMap<Long, String> = mutableMapOf()

    override suspend fun getIcons(itemIds: Set<Long>): Map<Long, String> =
        itemMedia.filterKeys { it in itemIds }

    override suspend fun insert(icons: List<WowForeverItemMedia>) {
        icons.forEach { itemMedia.putIfAbsent(it.itemId, it.icon) }
    }

    override suspend fun state(): List<WowForeverItemMedia> =
        itemMedia.toSortedMap().map { (itemId, icon) -> WowForeverItemMedia(itemId, icon) }

    override suspend fun withState(initialState: List<WowForeverItemMedia>): WowForeverItemMediaRepository {
        insert(initialState)
        return this
    }

    override fun clear() {
        itemMedia.clear()
    }
}
