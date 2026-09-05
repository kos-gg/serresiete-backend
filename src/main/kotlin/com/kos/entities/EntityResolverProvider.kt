package com.kos.entities

import com.kos.views.Game

class EntityResolverProvider(
    private val wowResolver: EntityResolver,
    private val wowHardcoreResolver: EntityResolver,
    private val lolResolver: EntityResolver
) {
    fun resolverFor(game: Game): EntityResolver = when (game) {
        Game.WOW -> wowResolver
        Game.WOW_HC -> wowHardcoreResolver
        Game.LOL -> lolResolver
    }
}
