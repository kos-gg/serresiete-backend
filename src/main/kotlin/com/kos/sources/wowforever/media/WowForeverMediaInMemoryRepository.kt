package com.kos.sources.wowforever.media

import com.kos.common.InMemoryRepository

class WowForeverMediaInMemoryRepository : WowForeverMediaRepository, InMemoryRepository {

    private data class Key(val kind: WowForeverMediaKind, val id: Long)

    private val media: MutableMap<Key, WowForeverMedia> = mutableMapOf()

    override suspend fun getIcons(kind: WowForeverMediaKind, ids: Set<Long>): Map<Long, String> =
        media.values
            .filter { it.kind == kind && it.id in ids && it.status == WowForeverMediaStatus.RESOLVED }
            .mapNotNull { row -> row.icon?.let { row.id to it } }
            .toMap()

    override suspend fun registerPending(kind: WowForeverMediaKind, region: String, ids: Set<Long>) {
        ids.forEach { id ->
            media.putIfAbsent(Key(kind, id), WowForeverMedia(kind, id, region, null, WowForeverMediaStatus.PENDING, 0))
        }
    }

    override suspend fun pending(limit: Int): List<WowForeverPendingMedia> =
        sorted()
            .filter { it.status == WowForeverMediaStatus.PENDING }
            .take(limit)
            .map { WowForeverPendingMedia(it.kind, it.id, it.region) }

    override suspend fun resolve(kind: WowForeverMediaKind, id: Long, icon: String) {
        updatePending(kind, id) { it.copy(icon = icon, status = WowForeverMediaStatus.RESOLVED) }
    }

    override suspend fun markUnavailable(kind: WowForeverMediaKind, id: Long) {
        updatePending(kind, id) { it.copy(status = WowForeverMediaStatus.UNAVAILABLE) }
    }

    override suspend fun markFailed(kind: WowForeverMediaKind, id: Long, maxAttempts: Int) {
        updatePending(kind, id) {
            val attempts = it.attempts + 1
            it.copy(
                attempts = attempts,
                status = if (attempts >= maxAttempts) WowForeverMediaStatus.UNAVAILABLE else it.status
            )
        }
    }

    override suspend fun state(): List<WowForeverMedia> = sorted()

    override suspend fun withState(initialState: List<WowForeverMedia>): WowForeverMediaRepository {
        initialState.forEach { media[Key(it.kind, it.id)] = it }
        return this
    }

    override fun clear() {
        media.clear()
    }

    private fun sorted(): List<WowForeverMedia> = media.values.sortedWith(compareBy({ it.kind }, { it.id }))

    private fun updatePending(kind: WowForeverMediaKind, id: Long, change: (WowForeverMedia) -> WowForeverMedia) {
        media.computeIfPresent(Key(kind, id)) { _, row ->
            if (row.status == WowForeverMediaStatus.PENDING) change(row) else row
        }
    }
}
