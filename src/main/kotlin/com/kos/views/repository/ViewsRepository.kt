package com.kos.views.repository

import com.kos.common.WithState
import com.kos.views.*

data class ViewsState(
    val views: List<SimpleView>,
    val viewEntities: List<ViewEntity>
)

interface ViewsRepository : WithState<ViewsState, ViewsRepository> {
    suspend fun getOwnViews(owner: String): List<SimpleView>
    suspend fun get(id: String): SimpleView?
    suspend fun create(
        id: String,
        name: String,
        owner: String,
        entitiesIds: List<Pair<Long, String?>>,
        game: Game,
        featured: Boolean,
        extraArguments: ViewExtraArguments? = null
    ): SimpleView

    suspend fun edit(
        id: String,
        name: String,
        published: Boolean,
        entities: List<Pair<Long, String?>>,
        featured: Boolean
    ): ViewModified

    suspend fun patch(
        id: String,
        name: String?,
        published: Boolean?,
        entities: List<Pair<Long, String?>>?,
        featured: Boolean?
    ): ViewPatched

    suspend fun delete(id: String): Unit
    suspend fun getViews(game: Game?, featured: Boolean, page: Int?, limit: Int?): Pair<ViewMetadata, List<SimpleView>>

    suspend fun getViewEntity(viewId: String, entityId: Long): ViewEntity?
}