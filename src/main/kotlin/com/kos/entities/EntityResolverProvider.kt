package com.kos.entities

import com.kos.views.Game

class EntityResolverProvider(
    private val wowResolver: EntityResolver,
    private val wowGuildResolver: GuildResolver<*, *>,
    private val wowHardcoreResolver: EntityResolver,
    private val wowHardcoreGuildResolver: GuildResolver<*, *>,
    private val lolResolver: EntityResolver,
    private val wowForeverResolver: EntityResolver,
    private val wowForeverGuildResolver: GuildResolver<*, *>
) {
    fun resolverFor(game: Game): EntityResolver = when (game) {
        Game.WOW -> wowResolver
        Game.WOW_HC -> wowHardcoreResolver
        Game.LOL -> lolResolver
        Game.WOW_FOREVER -> wowForeverResolver
    }

    fun guildResolverFor(game: Game): GuildResolver<*, *> = when (game) {
        Game.WOW -> wowGuildResolver
        Game.WOW_HC -> wowHardcoreGuildResolver
        Game.WOW_FOREVER -> wowForeverGuildResolver
        Game.LOL -> error("$game has no guilds")
    }
}
