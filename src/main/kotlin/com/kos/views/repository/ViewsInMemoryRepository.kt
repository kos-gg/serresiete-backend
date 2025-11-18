package com.kos.views.repository

import com.kos.common.InMemoryRepository
import com.kos.common.fold
import com.kos.views.*

class ViewsInMemoryRepository : ViewsRepository, InMemoryRepository {

    private val views: MutableList<SimpleView> = mutableListOf()
    private val viewEntities: MutableList<ViewEntity> = mutableListOf()

    override suspend fun getOwnViews(owner: String): List<SimpleView> = views.filter { it.owner == owner }

    override suspend fun get(id: String): SimpleView? = views.find { it.id == id }

    override suspend fun create(
        id: String,
        name: String,
        owner: String,
        entitiesIds: List<Pair<Long, String?>>,
        game: Game,
        featured: Boolean,
        extraArguments: ViewExtraArguments?
    ): SimpleView {
        val simpleView = SimpleView(id, name, owner, true, entitiesIds.map { it.first }, game, featured, extraArguments)
        views.add(simpleView)
        entitiesIds.forEach { viewEntities.add(ViewEntity(it.first, id, it.second)) }
        return simpleView
    }

    override suspend fun edit(
        id: String,
        name: String,
        published: Boolean,
        entities: List<Pair<Long, String?>>,
        featured: Boolean
    ): ViewModified {
        val index = views.indexOfFirst { it.id == id }
        val oldView = views[index]
        views.removeAt(index)
        views.add(
            index,
            SimpleView(id, name, oldView.owner, published, entities.map { it.first }, oldView.game, featured)
        )
        viewEntities.removeIf { it.viewId == id }
        viewEntities.addAll(entities.map { ViewEntity(it.first, id, it.second) })
        return ViewModified(id, name, published, entities.map { it.first }, featured)
    }

    override suspend fun patch(
        id: String,
        name: String?,
        published: Boolean?,
        entities: List<Pair<Long, String?>>?,
        featured: Boolean?
    ): ViewPatched {
        val index = views.indexOfFirst { it.id == id }
        val oldView = views[index]
        views.removeAt(index)
        val simpleView = SimpleView(
            id,
            name ?: oldView.name,
            oldView.owner,
            published ?: oldView.published,
            entities?.map { it.first } ?: oldView.entitiesIds,
            oldView.game,
            featured ?: oldView.featured
        )
        views.add(
            index,
            simpleView
        )
        if (entities != null) {
            viewEntities.removeIf { it.viewId == id }
            viewEntities.addAll(entities.map { ViewEntity(it.first, id, it.second) })
        }
        return ViewPatched(id, name, published, entities?.map { it.first }, featured)
    }

    override suspend fun delete(id: String): Unit {
        val index = views.indexOfFirst { it.id == id }
        views.removeAt(index)
        viewEntities.removeIf { it.viewId == id }
    }

    override suspend fun getViews(
        game: Game?,
        featured: Boolean,
        page: Int?,
        limit: Int?
    ): Pair<ViewMetadata, List<SimpleView>> {
        val allViews = views.toList()
        val maybeFeaturedViews = if (featured) allViews.filter { it.featured } else allViews

        val filteredQuery = game.fold(
            { maybeFeaturedViews },
            { maybeFeaturedViews.filter { it.game == game } }
        )

        val views = limit.fold(
            { filteredQuery },
            { filteredQuery.drop(((page ?: 1) - 1) * it).take(it) }
        )

        return Pair(ViewMetadata(allViews.count()), views)
    }

    override suspend fun getViewEntity(viewId: String, entityId: Long): ViewEntity? {
        return viewEntities.firstOrNull { it.entityId == entityId && it.viewId == viewId }
    }

    override suspend fun state(): ViewsState {
        return ViewsState(views, viewEntities)
    }

    override suspend fun withState(initialState: ViewsState): ViewsInMemoryRepository {
        views.addAll(initialState.views)
        viewEntities.addAll(initialState.viewEntities)

        return this
    }

    override fun clear() {
        views.clear()
        viewEntities.clear()
    }

    //TODO: this operation will be removed upon having parent character table implemented
    fun deleteCharacterFromViews(characterId: Long) {
        val viewsWithoutDeletedCharacter =
            views.map { view -> view.copy(entitiesIds = view.entitiesIds.filterNot { it == characterId }) }

        views.clear()
        views.addAll(viewsWithoutDeletedCharacter)
    }
}