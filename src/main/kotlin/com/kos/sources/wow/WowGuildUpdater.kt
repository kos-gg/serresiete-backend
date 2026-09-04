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
) : EntityUpdater<Pair<GuildPayload, String>>, WithLogger("WowGuildUpdater") {

    override suspend fun update(entities: List<Pair<GuildPayload, String>>): List<ServiceError> {

        val guilds = entities.groupBy { it.first.blizzardId }

        val (initialErrors, resolvedGuilds) = guilds.values.map { rows ->
            val guild = rows.first().first
            val viewIds = rows.map { it.second }
            resolver.resolveRoster(guild.region, guild.realm, guild.name)
                .map { (_, roster) -> Triple(guild, viewIds, roster) }
        }.split()

        val downstreamErrors = resolvedGuilds.flatMap { (guild, viewIds, roster) ->
            logger.info("Updating Wow Guild ${guild.name} - ${guild.realm} - ${guild.region} (${viewIds.size} view(s))")

            val (current, new) = resolver.getCurrentAndNewEntities(entitiesRepository, roster, Game.WOW)
            val newMembers = resolver.resolveGuildMembers(new)

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

                    val currentRoster = viewsRepository.get(viewIds.first())?.entitiesIds?.toSet()

                    val notInRoster =
                        currentRoster?.minus((insertedWithAlias.map { it.first } + current.map { it.value.id }).toSet())
                    notInRoster?.let {
                        if (notInRoster.isNotEmpty()) {
                            viewIds.forEach { viewId ->
                                logger.info("Disassociating ${notInRoster.size} entities from viewId $viewId and guild ${guild.name}")
                                viewsRepository.disassociateEntitiesFromView(notInRoster, viewId)
                            }
                        }
                    }

                    if (insertedWithAlias.isNotEmpty()) {
                        viewIds.forEach { viewId ->
                            logger.info("Associating [${insertedWithAlias.map { it.first }}] entities to viewId $viewId and guild ${guild.name}")
                            viewsRepository.associateEntitiesIdsToView(insertedWithAlias, viewId)
                        }
                    }

                    logger.info("Finished updating Wow Guild ${guild.name} - ${guild.realm} - ${guild.region}")
                    emptyList()
                }
            )

            memberErrors
        }

        return initialErrors + downstreamErrors
    }
}
