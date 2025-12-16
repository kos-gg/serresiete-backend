package com.kos.entities.entitiesUpdaters

import com.kos.common.error.ControllerError
import com.kos.common.split
import com.kos.entities.GuildPayload
import com.kos.entities.entitiesResolvers.WowHardcoreResolver
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsRepository

class WowHardcoreGuildUpdater(
    private val resolver: WowHardcoreResolver,
    private val entitiesRepository: EntitiesRepository,
    private val viewsRepository: ViewsRepository
) : EntityUpdater<Pair<GuildPayload, String>> {
    override suspend fun update(entities: List<Pair<GuildPayload, String>>): List<ControllerError> {
        val (errors, rostersWithViewIds) = entities
            .map { (guild, viewId) ->
                resolver.resolveRoster(guild.region, guild.realm, guild.name).map { (_, roster) -> roster to viewId }
            }.split()

        return rostersWithViewIds.flatMap { (requests, viewId) ->
            resolver.resolve(requests, null).fold(
                ifLeft = { errors + it },
                ifRight = { resolvedEntities ->
                    entitiesRepository.insert(resolvedEntities.entities.map { it.first }, Game.WOW_HC).fold(
                        ifLeft = { errors + it },
                        ifRight = { insertedEntities ->
                            val entitiesToAssociate =
                                insertedEntities.map { it.id to null } + resolvedEntities.existing.map { it.first.id to it.second }
                            viewsRepository.associateEntitiesIdsToView(entitiesToAssociate, viewId)
                            errors
                        }
                    )
                }
            )
        }
    }
}