package com.kos.sources.wowhc.staticdata.wowitems

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.common.WithState
import com.kos.datacache.domain.WowItemDetails

data class WowItemsState(
    val wowItems: List<WowItemState>
)

data class WowItemState(
    val id: Long,
    val item: String,
    val media: String
)

interface WowItemsRepository : WithState<WowItemsState, WowItemsRepository> {
    suspend fun getItemMedia(id: Long): Either<ClientError, String?>
    suspend fun getItem(id: Long): Either<ClientError, WowItemDetails>
}