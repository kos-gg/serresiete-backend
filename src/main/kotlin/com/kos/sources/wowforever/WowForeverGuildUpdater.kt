package com.kos.sources.wowforever

import arrow.core.Either
import com.kos.common.WithLogger
import com.kos.entities.EntityUpdater
import com.kos.entities.GuildUpdateFailed
import com.kos.entities.PartialFailure
import com.kos.entities.domain.WowForeverGuild
import com.kos.entities.domain.WowForeverEntity
import com.kos.entities.domain.WowForeverEntityRequest
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsRepository

class WowForeverGuildUpdater(
    private val guildResolver: WowForeverGuildResolver,
    private val entitiesRepository: EntitiesRepository,
    private val viewsRepository: ViewsRepository
) : EntityUpdater<Pair<WowForeverGuild, String>>, WithLogger("WowForeverGuildUpdater") {

    override suspend fun update(entities: List<Pair<WowForeverGuild, String>>): List<PartialFailure> =
        entities.groupBy { it.first.blizzardId }
            .values
            .mapNotNull { rows ->
                val guild = rows.first().first
                Either.catch { updateGuild(guild, rows.map { it.second }) }
                    .leftOrNull()
                    ?.let { GuildUpdateFailed(guild, it) }
            }

    private suspend fun updateGuild(guild: WowForeverGuild, viewIds: List<String>) {
        logger.info("Updating Wow Forever Guild ${guild.name} - ${guild.region} (${viewIds.size} view(s))")
        val (_, roster) = guildResolver.resolveRoster(guild.region, guild.name)

        val (tracked, newMembers) = guildResolver.trackedAndNewMembers(roster)
        renameTrackedMembers(tracked)

        val inserted = entitiesRepository.insert(newMembers, Game.WOW_FOREVER)
        logger.info("Inserted new entities $inserted to EntityRepository")

        val inGuild = (tracked.map { (_, entity) -> entity.id } + inserted.map { it.id }).toSet()
        syncViewMembership(guild, viewIds, inGuild)

        logger.info("Finished updating Wow Forever Guild ${guild.name} - ${guild.region}")
    }

    private suspend fun renameTrackedMembers(tracked: List<Pair<WowForeverEntityRequest, WowForeverEntity>>) =
        tracked
            .filter { (member, entity) -> member.name != entity.name || member.surname != entity.surname }
            .forEach { (member, entity) -> entitiesRepository.update(entity.id, member, Game.WOW_FOREVER) }

    private suspend fun syncViewMembership(guild: WowForeverGuild, viewIds: List<String>, inGuild: Set<Long>) {
        val currentlyAssociated = viewsRepository.get(viewIds.first())?.entitiesIds?.toSet().orEmpty()
        val noLongerInGuild = currentlyAssociated - inGuild
        val joinedGuild = inGuild - currentlyAssociated

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
