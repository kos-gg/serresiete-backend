package com.kos.sources.wowforever

import arrow.core.Either
import arrow.core.getOrElse
import com.kos.clients.ClientError
import com.kos.clients.NotFound
import com.kos.clients.blizzard.BlizzardClient
import com.kos.common.WithLogger
import com.kos.entities.GuildMembers
import com.kos.entities.GuildResolver
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game

class WowForeverGuildResolver(
    override val entitiesRepository: EntitiesRepository,
    private val blizzardClient: BlizzardClient
) : GuildResolver<WowForeverGuild, WowForeverEntityRequest>, WithLogger("WowForeverGuildResolver") {

    override val game = Game.WOW_FOREVER

    override suspend fun fetchRoster(guild: EntityRequest): GuildMembers<WowForeverGuild, WowForeverEntityRequest> {
        val guildReq = guild as WowForeverEntityRequest
        logger.info("Resolving guild ${guildReq.name} (${guildReq.region})")

        val (guildRoster, members) = resolveRoster(guildReq.region, guildReq.name)
        return GuildMembers(payload(guildRoster, guildReq), members)
    }

    override suspend fun admit(newMembers: List<WowForeverEntityRequest>): List<Pair<InsertEntityRequest, String?>> =
        newMembers.map { it to null }

    override fun hasChanged(member: WowForeverEntityRequest, tracked: Entity): Boolean =
        (tracked as WowForeverEntity).let { member.name != it.name || member.surname != it.surname }

    suspend fun exists(guild: WowForeverEntityRequest): Either<ClientError, WowForeverGuild> =
        blizzardClient.getForeverGuildRoster(guild.region, guild.name).map { payload(it, guild) }

    private fun payload(guildRoster: WowForeverGuildRoster, guild: WowForeverEntityRequest): WowForeverGuild =
        WowForeverGuild(
            guild.name.lowercase(),
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
