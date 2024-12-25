package com.kos.views.repository

import com.kos.common.WithState
import com.kos.views.*

interface ViewsRepository : WithState<List<SimpleView>, ViewsRepository> {
    suspend fun getOwnViews(owner: String): List<SimpleView>
    suspend fun get(id: String): SimpleView?
    suspend fun create(
        id: String,
        name: String,
        owner: String,
        characterIds: List<Long>,
        game: Game,
        featured: Boolean
    ): SimpleView

    suspend fun edit(
        id: String,
        name: String,
        published: Boolean,
        characters: List<Long>,
        featured: Boolean
    ): ViewModified

    suspend fun patch(
        id: String,
        name: String?,
        published: Boolean?,
        characters: List<Long>?,
        featured: Boolean?
    ): ViewPatched

    suspend fun delete(id: String): Unit
    suspend fun getViews(game: Game?, featured: Boolean, page: Int?, limit: Int?): Pair<ViewMetadata, List<SimpleView>>
}