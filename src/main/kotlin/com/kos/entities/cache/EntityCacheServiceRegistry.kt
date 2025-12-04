package com.kos.entities.cache

import com.kos.views.Game

class EntityCacheServiceRegistry(
    private val services: List<EntityCacheService>
) {

    fun serviceFor(game: Game): EntityCacheService =
        services.first { it.game == game }
}
