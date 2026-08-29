package com.kos.sources.wow

import com.kos.common.WithLogger
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
) : EntityUpdater<Pair<GuildPayload, String>>, WithLogger("wowGuildUpdater") {

    override suspend fun update(entities: List<Pair<GuildPayload, String>>): List<ServiceError> {

        val guild = entities.first().first
        logger.info("Updating Wow Guild ${guild.name} - ${guild.realm} - ${guild.region}")

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
                    logger.info("Inserted new entities $inserted to EntityRepository")

                    val insertedWithAlias =
                        inserted.zip(newMembers) { entity, member ->
                            entity.id to member.second
                        }

                    val currentRoster = viewsRepository.get(viewId)?.entitiesIds?.toSet()

                    val entitiesThatLeftGuild =
                        currentRoster?.minus((insertedWithAlias.map { it.first } + current.map { it.value.id }).toSet())
                    entitiesThatLeftGuild?.let {
                        if (it.isNotEmpty()) {
                            logger.info("Disassociating ${it.size} entities from viewId $viewId and guild ${guild.name}")
                            viewsRepository.disassociateEntitiesFromView(it, viewId)
                        }
                    }

                    if (insertedWithAlias.isNotEmpty()) {
                        logger.info("Associating [${insertedWithAlias.map { it.first }}] entities to viewId $viewId and guild ${guild.name}")
                        viewsRepository.associateEntitiesIdsToView(
                            insertedWithAlias,
                            viewId
                        )
                    }

                    logger.info("Finished updating Wow Guild ${guild.name} - ${guild.realm} - ${guild.region}")
                    emptyList()
                }
            )

            memberErrors + unchecked.map { it.second }
        }

        return initialErrors + downstreamErrors
    }
}
