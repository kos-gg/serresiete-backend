package com.kos.sources.wowhc

import com.kos.common.error.ServiceError
import com.kos.common.error.toEntityResolverError
import com.kos.common.split
import com.kos.entities.EntityUpdater
import com.kos.entities.domain.GuildPayload
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsRepository

class WowHardcoreGuildUpdater(
    private val resolver: WowHardcoreEntityResolver,
    private val entitiesRepository: EntitiesRepository,
    private val viewsRepository: ViewsRepository
) : EntityUpdater<Pair<GuildPayload, String>> {
    override suspend fun update(
        entities: List<Pair<GuildPayload, String>>
    ): List<ServiceError> {

        val initialResults = entities
            .map { (guild, viewId) ->
                resolver
                    .resolveRoster(guild.region, guild.realm, guild.name)
                    .map { (_, roster) -> roster to viewId }
            }

        val (initialErrors, rostersWithViewIds) = initialResults.split()

        val downstreamErrors = rostersWithViewIds.flatMap { (requests, viewId) ->

            resolver.resolve(requests, null).fold(
                ifLeft = { listOf(it) },

                ifRight = { resolved ->
                    entitiesRepository
                        .insert(resolved.entities.map { it.first }, Game.WOW_HC)
                        .fold(
                            ifLeft = {
                                listOf(it.toEntityResolverError(Game.WOW_HC, it.message))
                            },
                            ifRight = { inserted ->
                                val entitiesToAssociate =
                                    inserted.map { it.id to null } +
                                            resolved.existing.map { it.first.id to it.second }

                                viewsRepository.associateEntitiesIdsToView(
                                    entitiesToAssociate,
                                    viewId
                                )

                                emptyList()
                            }
                        )
                }
            )
        }

        return initialErrors + downstreamErrors
    }

}