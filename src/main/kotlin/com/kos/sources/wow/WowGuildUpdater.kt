package com.kos.sources.wow

import com.kos.common.error.ServiceError
import com.kos.common.error.toEntityResolverError
import com.kos.common.split
import com.kos.entities.EntityUpdater
import com.kos.entities.domain.GuildPayload
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsRepository

class WowGuildUpdater(
    private val resolver: WowEntityResolver,
    private val entitiesRepository: EntitiesRepository,
    private val viewsRepository: ViewsRepository
) : EntityUpdater<Pair<GuildPayload, String>> {

    override suspend fun update(entities: List<Pair<GuildPayload, String>>): List<ServiceError> {

        val (initialErrors, resolvedRostersWithViewId) =
            entities.map { (guild, viewId) ->
                resolver
                    .resolveRoster(guild.region, guild.realm, guild.name)
                    .map { (_, roster) -> roster to viewId }
            }.split()

        val downstreamErrors = resolvedRostersWithViewId.flatMap { (roster, viewId) ->
            val (current, new) = resolver.getCurrentAndNewEntities(entitiesRepository, roster, Game.WOW)
            val (newMembers, unchecked) = resolver.resolveGuildMembers(new)

            val memberErrors = entitiesRepository.insert(newMembers.map { it.first }, Game.WOW).fold(
                ifLeft = { insertError ->
                    listOf(insertError.toEntityResolverError(Game.WOW, insertError.message))
                },
                ifRight = { inserted ->
                    val insertedWithAlias =
                        inserted.zip(newMembers) { entity, member ->
                            entity.id to member.second
                        }

                    val currentRoster = viewsRepository.get(viewId)?.entitiesIds?.toSet()

                    currentRoster?.minus((insertedWithAlias.map { it.first } + current.map { it.value.id }).toSet())
                        ?.let { viewsRepository.disassociateEntitiesFromView(it, viewId) }

                    if (insertedWithAlias.isNotEmpty()) viewsRepository.associateEntitiesIdsToView(insertedWithAlias, viewId)

                    emptyList()
                }
            )

            memberErrors + unchecked.map { it.second }
        }

        return initialErrors + downstreamErrors
    }
}
