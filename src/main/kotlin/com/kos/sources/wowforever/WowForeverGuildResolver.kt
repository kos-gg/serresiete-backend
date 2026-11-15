package com.kos.sources.wowforever

import arrow.core.Either
import arrow.core.getOrElse
import com.kos.clients.ClientError
import com.kos.clients.NotFound
import com.kos.clients.blizzard.BlizzardClient
import com.kos.common.WithLogger
import com.kos.entities.domain.*
import com.kos.entities.guilds.GuildResolver
import com.kos.entities.guilds.GuildWithRoster
import com.kos.entities.guilds.WowForeverGuild
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game

class WowForeverGuildResolver(
    override val entitiesRepository: EntitiesRepository,
    private val blizzardClient: BlizzardClient
) : GuildResolver<WowForeverGuild, WowForeverEntityRequest>, WithLogger("WowForeverGuildResolver") {

    override val game = Game.WOW_FOREVER

    override suspend fun resolveGuild(guild: EntityRequest): GuildWithRoster<WowForeverGuild, WowForeverEntityRequest> {
        val guildRequest = guild as WowForeverEntityRequest
        logger.info("Resolving guild ${guildRequest.name} (${guildRequest.region})")

        val roster =
            blizzardClient
                .getForeverGuildRoster(guildRequest.region, guildRequest.name)
                .getOrElse {
                    if (it is NotFound) error("Guild ${guildRequest.name} (${guildRequest.region}) not found")
                    else error("Third party call failed: $it")
                }

        val wowForeverEntityRequests = roster.members.map {
            WowForeverEntityRequest(
                it.name.lowercase(),
                it.surname.lowercase(),
                guildRequest.region,
                it.id
            )
        }

        return GuildWithRoster(payload(roster.guildId, guildRequest), wowForeverEntityRequests)
    }

    override suspend fun resolveRoster(newMembers: List<WowForeverEntityRequest>): List<InsertEntityRequest> =
        newMembers

    override fun hasGuildMemberChanged(member: WowForeverEntityRequest, tracked: Entity): Boolean =
        (tracked as WowForeverEntity).let { member.name != it.name || member.surname != it.surname }

    suspend fun exists(guild: WowForeverEntityRequest): Either<ClientError, WowForeverGuild> =
        blizzardClient.getForeverGuildRoster(guild.region, guild.name)
            .map { payload(it.guildId, guild) }

    private fun payload(guildId: Long, guild: WowForeverEntityRequest): WowForeverGuild =
        WowForeverGuild(
            guild.name.lowercase(),
            guild.region.lowercase(),
            guildId
        )
}
