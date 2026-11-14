package com.kos.entities.guilds

import arrow.core.Either
import com.kos.common.WithLogger
import com.kos.entities.EntityUpdater
import com.kos.entities.GuildUpdateFailed
import com.kos.entities.PartialFailure
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.repository.ViewsRepository

class GuildUpdater<G : Guild, M : InsertEntityRequest>(
    private val guildResolver: GuildResolver<G, M>,
    private val entitiesRepository: EntitiesRepository,
    private val viewsRepository: ViewsRepository
) : EntityUpdater<Pair<G, String>>, WithLogger("GuildUpdater") {

    override suspend fun update(entities: List<Pair<G, String>>): List<PartialFailure> =
        entities.groupBy { it.first.blizzardId }
            .values
            .mapNotNull { rows ->
                val guild = rows.first().first
                Either.catch { updateGuild(guild, rows.map { it.second }) }
                    .leftOrNull()
                    ?.let { GuildUpdateFailed(guild, it) }
            }

    private suspend fun updateGuild(guild: G, viewIds: List<String>) {
        logger.info("Updating ${guildResolver.game} guild ${guild.description()} (${viewIds.size} view(s))")
        val resolved = guildResolver.resolve(guild.toRequest())

        renameChangedMembers(resolved)

        val inserted = entitiesRepository.insert(resolved.newMembers, guildResolver.game)
        logger.info("Inserted new entities $inserted to EntityRepository")

        val inGuild = (resolved.existing.map { (_, entity) -> entity.id } + inserted.map { it.id }).toSet()
        syncViewMembership(guild, viewIds, inGuild)

        logger.info("Finished updating ${guildResolver.game} guild ${guild.description()}")
    }

    private suspend fun renameChangedMembers(resolved: ResolvedGuild<G, M>) =
        resolved.existing
            .filter { (member, entity) -> guildResolver.hasGuildMemberChanged(member, entity) }
            .forEach { (member, entity) -> entitiesRepository.update(entity.id, member, guildResolver.game) }

    private fun G.description(): String = "$name (${listOfNotNull(region, realmOrNull()).joinToString("/")})"

    private suspend fun syncViewMembership(guild: G, viewIds: List<String>, inGuild: Set<Long>) {
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
