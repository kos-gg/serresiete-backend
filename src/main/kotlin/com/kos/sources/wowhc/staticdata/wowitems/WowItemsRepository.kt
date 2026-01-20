package com.kos.sources.wowhc.staticdata.wowitems

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.GetWowItemResponse
import com.kos.clients.domain.GetWowMediaResponse
import com.kos.common.WithState

data class WowItemsState(
    val wowItems: List<WowItemState>
)

data class WowItemState(
    val id: Long,
    val item: String,
    val media: String
)

interface WowItemsRepository : WithState<WowItemsState, WowItemsRepository> {
    suspend fun getItemMedia(id: Long): Either<ClientError, GetWowMediaResponse>
    suspend fun getItem(id: Long): Either<ClientError, GetWowItemResponse>
}