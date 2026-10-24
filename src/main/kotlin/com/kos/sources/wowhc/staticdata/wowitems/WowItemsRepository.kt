package com.kos.sources.wowhc.staticdata.wowitems

import com.kos.common.WithState
import com.kos.sources.wowhc.WowItemDetails

data class WowItemsState(
    val wowItems: List<WowItemState>
)

data class WowItemState(
    val id: Long,
    val item: String,
    val media: String
)

interface WowItemsRepository : WithState<WowItemsState, WowItemsRepository> {
    suspend fun getItemMedia(id: Long): String?
    suspend fun getItem(id: Long): WowItemDetails?
}