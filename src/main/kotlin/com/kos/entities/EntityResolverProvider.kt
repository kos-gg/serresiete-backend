package com.kos.entities

import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import com.kos.views.WowExtraArguments

class EntityResolverProvider(
    private val wowResolver: EntityResolver,
    private val wowGuildResolver: EntityResolver,
    private val wowHardcoreResolver: EntityResolver,
    private val wowHardcoreGuildResolver: EntityResolver,
    private val lolResolver: EntityResolver
) {
    fun resolverFor(game: Game, extra: ViewExtraArguments?): EntityResolver {
        val isGuild = (extra as? WowExtraArguments)?.isGuild == true
        return when (game) {
            Game.WOW -> if (isGuild) wowGuildResolver else wowResolver
            Game.WOW_HC -> if (isGuild) wowHardcoreGuildResolver else wowHardcoreResolver
            Game.LOL -> lolResolver
        }
    }
}
