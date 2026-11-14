package com.kos.sources.wowhc

import arrow.core.Either
import com.kos.entities.EntityUpdater
import com.kos.entities.GuildUpdateFailed
import com.kos.entities.PartialFailure
import com.kos.entities.domain.WowGuild
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsRepository

class WowHardcoreGuildUpdater(
    private val guildResolver: WowHardcoreGuildResolver,
    private val resolver: WowHardcoreEntityResolver,
    private val entitiesRepository: EntitiesRepository,
    private val viewsRepository: ViewsRepository
) : EntityUpdater<Pair<WowGuild, String>> {

    override suspend fun update(
        entities: List<Pair<WowGuild, String>>
    ): List<PartialFailure> =
        entities.mapNotNull { (guild, viewId) ->
            Either.catch { updateGuild(guild, viewId) }
                .leftOrNull()
                ?.let { GuildUpdateFailed(guild, it) }
        }

    private suspend fun updateGuild(guild: WowGuild, viewId: String) {
        val (_, roster) = guildResolver.resolveRoster(guild.region, guild.realm, guild.name)
        val resolved = resolver.resolve(roster)
        val inserted = entitiesRepository.insert(resolved.entities.map { it.first }, Game.WOW_HC)
        val entitiesToAssociate =
            inserted.map { it.id to null } + resolved.existing.map { it.first.id to it.second }

        viewsRepository.associateEntitiesIdsToView(entitiesToAssociate, viewId)
    }
}
