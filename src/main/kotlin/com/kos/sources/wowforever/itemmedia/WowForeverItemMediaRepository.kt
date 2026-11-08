package com.kos.sources.wowforever.itemmedia

import com.kos.common.WithState

data class WowForeverItemMedia(val itemId: Long, val icon: String)

interface WowForeverItemMediaRepository : WithState<List<WowForeverItemMedia>, WowForeverItemMediaRepository> {
    suspend fun getIcons(itemIds: Set<Long>): Map<Long, String>
    suspend fun insert(icons: List<WowForeverItemMedia>)
}
