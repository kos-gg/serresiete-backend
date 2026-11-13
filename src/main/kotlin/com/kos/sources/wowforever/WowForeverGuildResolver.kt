package com.kos.sources.wowforever

import arrow.core.Either
import arrow.core.getOrElse
import com.kos.clients.ClientError
import com.kos.clients.NotFound
import com.kos.clients.blizzard.BlizzardClient
import com.kos.common.WithLogger
import com.kos.entities.EntityResolver
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game

class WowForeverGuildResolver(
    private val repo: EntitiesRepository,
    private val blizzardClient: BlizzardClient
) : EntityResolver, WithLogger("WowForeverGuildResolver") {

    override suspend fun resolve(requested: List<EntityRequest>): ResolvedEntities {
        val guildReq = requested.first() as WowForeverEntityRequest
        logger.info("Resolving guild ${guildReq.name} (${guildReq.region})")

        val (guildRoster, members) = resolveRoster(guildReq.region, guildReq.name)
        val (tracked, newMembers) = trackedAndNewMembers(members)

        return ResolvedEntities(
            entities = newMembers.map { it to null },
            existing = tracked.map { (_, entity) -> entity to null },
            unchecked = emptyList(),
            guild = payload(guildRoster, guildReq)
        )
    }

    suspend fun exists(guild: WowForeverEntityRequest): Either<ClientError, GuildPayload> =
        blizzardClient.getForeverGuildRoster(guild.region, guild.name).map { payload(it, guild) }

    suspend fun trackedAndNewMembers(
        members: List<WowForeverEntityRequest>
    ): Pair<List<Pair<WowForeverEntityRequest, WowForeverEntity>>, List<WowForeverEntityRequest>> {
        val lookups = members.map { member -> member to trackedByBlizzardId(member) }
        val tracked = lookups.mapNotNull { (member, entity) -> entity?.let { member to it } }
        val newMembers = lookups.filter { (_, entity) -> entity == null }.map { (member, _) -> member }
        return tracked to newMembers
    }

    private suspend fun trackedByBlizzardId(member: InsertEntityRequest): WowForeverEntity? =
        repo.get(member, Game.WOW_FOREVER) as WowForeverEntity?

    private fun payload(guildRoster: WowForeverGuildRoster, guild: WowForeverEntityRequest): GuildPayload =
        GuildPayload(
            guild.name.lowercase(),
            "",
            guild.region.lowercase(),
            guildRoster.guildId
        )

    suspend fun resolveRoster(
        region: String,
        name: String
    ): Pair<WowForeverGuildRoster, List<WowForeverEntityRequest>> {
        val roster = blizzardClient.getForeverGuildRoster(region, name).getOrElse {
            if (it is NotFound) error("Guild $name ($region) not found") else error("Third party call failed: $it")
        }

        val members = roster.members.map {
            WowForeverEntityRequest(it.name.lowercase(), it.surname.lowercase(), region, it.id)
        }

        return Pair(roster, members)
    }
}
