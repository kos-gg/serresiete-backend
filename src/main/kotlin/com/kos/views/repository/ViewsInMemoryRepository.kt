package com.kos.views.repository

import com.kos.common.InMemoryRepository
import com.kos.common.fold
import com.kos.views.*

class ViewsInMemoryRepository : ViewsRepository, InMemoryRepository {

    private val views: MutableList<SimpleView> = mutableListOf()

    override suspend fun getOwnViews(owner: String): List<SimpleView> = views.filter { it.owner == owner }

    override suspend fun get(id: String): SimpleView? = views.find { it.id == id }

    override suspend fun create(
        id: String,
        name: String,
        owner: String,
        characterIds: List<Long>,
        game: Game,
        featured: Boolean
    ): SimpleView {
        val simpleView = SimpleView(id, name, owner, true, characterIds, game, featured)
        views.add(simpleView)
        return simpleView
    }

    override suspend fun edit(
        id: String,
        name: String,
        published: Boolean,
        characters: List<Long>,
        featured: Boolean
    ): ViewModified {
        val index = views.indexOfFirst { it.id == id }
        val oldView = views[index]
        views.removeAt(index)
        views.add(index, SimpleView(id, name, oldView.owner, published, characters, oldView.game, featured))
        return ViewModified(id, name, published, characters, featured)
    }

    override suspend fun patch(
        id: String,
        name: String?,
        published: Boolean?,
        characters: List<Long>?,
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
            characters ?: oldView.characterIds,
            oldView.game,
            featured ?: oldView.featured
        )
        views.add(
            index,
            simpleView
        )
        return ViewPatched(id, name, published, characters, featured)
    }

    override suspend fun delete(id: String): ViewDeleted {
        val index = views.indexOfFirst { it.id == id }
        views.removeAt(index)
        return ViewDeleted(id)
    }

    override suspend fun getViews(game: Game?, featured: Boolean): List<SimpleView> {
        val allViews = views.toList()
        val maybeFeaturedViews = if (featured) allViews.filter { it.featured } else allViews

        return game.fold(
            { maybeFeaturedViews },
            { maybeFeaturedViews.filter { it.game == game } }
        )
    }

    override suspend fun state(): List<SimpleView> {
        return views
    }

    override suspend fun withState(initialState: List<SimpleView>): ViewsInMemoryRepository {
        views.addAll(initialState)
        return this
    }

    override fun clear() {
        views.clear()
    }
}