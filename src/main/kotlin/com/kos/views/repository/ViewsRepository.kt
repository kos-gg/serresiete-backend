package com.kos.views.repository

import arrow.core.Either
import com.kos.common.WithState
import com.kos.common.error.RepositoryError
import com.kos.views.*
import java.time.OffsetDateTime

data class ViewsState(
    val views: List<SimpleView>,
    val viewEntities: List<ViewEntity>
)

interface ViewsRepository : WithState<ViewsState, ViewsRepository> {
    suspend fun getOwnViews(owner: String, query: GetViewsQuery): Pair<ViewMetadata, List<SimpleView>>
    suspend fun get(id: String): SimpleView?
    suspend fun create(
        id: String,
        name: String,
        owner: String,
        entitiesIds: List<Pair<Long, String?>>,
        game: Game,
        featured: Boolean,
        extraArguments: ViewExtraArguments? = null
    ): Either<RepositoryError, SimpleView>

    suspend fun edit(
        id: String,
        name: String,
        published: Boolean,
        entities: List<Pair<Long, String?>>,
        featured: Boolean
    ): Either<RepositoryError, ViewModified>

    suspend fun patch(
        id: String,
        name: String?,
        published: Boolean?,
        entities: List<Pair<Long, String?>>?,
        featured: Boolean?
    ): Either<RepositoryError, ViewPatched>

    suspend fun delete(id: String): Either<RepositoryError, Unit>
    suspend fun getViews(query: GetViewsQuery): Pair<ViewMetadata, List<SimpleView>>

    suspend fun getViewEntity(viewId: String, entityId: Long): ViewEntity?

    suspend fun associateEntitiesIdsToView(entities: List<Pair<Long, String?>>, id: String)
    suspend fun disassociateEntitiesFromView(entities: Set<Long>, id: String)
    suspend fun updateLastSyncedAt(viewId: String, at: OffsetDateTime)
}