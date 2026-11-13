package com.kos.sources.wow

import arrow.core.Either
import com.kos.common.WithLogger
import com.kos.entities.EntityUpdater
import com.kos.entities.GuildUpdateFailed
import com.kos.entities.PartialFailure
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.getCurrentAndNewEntities
import com.kos.views.Game
import com.kos.views.repository.ViewsRepository

class WowGuildUpdater(
    private val guildResolver: WowGuildResolver,
    private val entitiesRepository: EntitiesRepository,
    private val viewsRepository: ViewsRepository
) : EntityUpdater<Pair<GuildPayload, String>>, WithLogger("WowGuildUpdater") {

    override suspend fun update(entities: List<Pair<GuildPayload, String>>): List<PartialFailure> =
        entities.groupBy { it.first.blizzardId }
            .values
            .mapNotNull { rows ->
                val guild = rows.first().first
                Either.catch { updateGuild(guild, rows.map { it.second }) }
                    .leftOrNull()
                    ?.let { GuildUpdateFailed(guild, it) }
            }

    private suspend fun updateGuild(guild: GuildPayload, viewIds: List<String>) {
        logger.info("Updating Wow Guild ${guild.name} - ${guild.realm} - ${guild.region} (${viewIds.size} view(s))")
        val (_, roster) = guildResolver.resolveRoster(guild.region, guild.realm, guild.name)

        val (current, newEntities) = entitiesRepository.getCurrentAndNewEntities(roster, Game.WOW)
        val (renamedEntities, newMembers) = updateAlreadyTrackedMembers(newEntities)

        val entityIdsStillInGuild = current.map { it.value.id }.toSet() + renamedEntities
        resolveAndInsertNewMembers(guild, viewIds, newMembers, entityIdsStillInGuild)
    }

    private suspend fun updateAlreadyTrackedMembers(
        newEntities: List<EntityRequest>
    ): Pair<List<Long>, List<WowEntityRequest>> {
        val (renamed, newMembers) = newEntities
            .map { it as WowEntityRequest }
            .map { request -> request to trackedMemberByBlizzardId(request) }
            .partition { (_, trackedEntity) -> trackedEntity != null }

        val renamedMembers = renamed.map { (request, trackedEntity) ->
            trackedEntity!!
            entitiesRepository.update(trackedEntity.id, request, Game.WOW)
            trackedEntity.id
        }

        return renamedMembers to newMembers.map { it.first }
    }

    private suspend fun trackedMemberByBlizzardId(request: WowEntityRequest): WowEntity? =
        request.blizzardId?.let { entitiesRepository.get(request as InsertEntityRequest, Game.WOW) as? WowEntity }

    private suspend fun resolveAndInsertNewMembers(
        guild: GuildPayload,
        viewIds: List<String>,
        newMembers: List<WowEntityRequest>,
        entityIdsStillInGuild: Set<Long>
    ) {
        val newMembers = guildResolver.resolveMembers(newMembers)

        val inserted = entitiesRepository.insert(newMembers.map { it.first }, Game.WOW)
        logger.info("Inserted new entities $inserted to EntityRepository")

        syncViewMembership(guild, viewIds, stillInGuild = entityIdsStillInGuild + inserted.map { it.id })

        logger.info("Finished updating Wow Guild ${guild.name} - ${guild.realm} - ${guild.region}")
    }

    private suspend fun syncViewMembership(guild: GuildPayload, viewIds: List<String>, stillInGuild: Set<Long>) {
        val currentlyAssociated = viewsRepository.get(viewIds.first())?.entitiesIds?.toSet().orEmpty()
        val noLongerInGuild = currentlyAssociated - stillInGuild
        val joinedGuild = stillInGuild - currentlyAssociated

        if (noLongerInGuild.isNotEmpty()) {
            viewIds.forEach { viewId ->
                logger.info("Disassociating ${noLongerInGuild.size} entities from viewId $viewId and guild ${guild.name}")
                viewsRepository.disassociateEntitiesFromView(noLongerInGuild, viewId)
            }
        }

        if (joinedGuild.isNotEmpty()) {
            viewIds.forEach { viewId ->
                logger.info("Associating $joinedGuild entities to viewId $viewId and guild ${guild.name}")
                viewsRepository.associateEntitiesIdsToView(joinedGuild.map { it to null }, viewId)
            }
        }
    }
}
