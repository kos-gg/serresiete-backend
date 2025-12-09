package com.kos.entities

import com.kos.views.Game

class EntityResolverProvider(private val resolvers: List<EntityResolver>) {
    fun resolverFor(game: Game): EntityResolver? =
        resolvers.firstOrNull { it.game == game }
}