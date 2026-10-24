package com.kos.sources.wowhc

import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.orThrow
import com.kos.entities.EntityResolver
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.GuildPayload
import com.kos.entities.domain.GuildRoster
import com.kos.entities.domain.ResolvedEntities
import com.kos.entities.domain.WowEntityRequest
import com.kos.views.Game

class WowHardcoreGuildResolver(
    private val blizzardClient: BlizzardClient,
    private val characterResolver: WowHardcoreEntityResolver
) : EntityResolver {
    companion object {
        private const val MIN_MEMBER_LEVEL = 10
    }

    override suspend fun resolve(requested: List<EntityRequest>): ResolvedEntities {
        val guildReq = requested.first() as WowEntityRequest
        val (guildRoster, members) = resolveRoster(guildReq.region, guildReq.realm, guildReq.name)

        return characterResolver.resolve(members).copy(guild = payload(guildRoster, guildReq))
    }

    private fun payload(guildRoster: GuildRoster, guild: WowEntityRequest): GuildPayload =
        GuildPayload(
            name = guild.name,
            realm = guild.realm,
            region = guild.region,
            blizzardId = guildRoster.guildId
        )

    suspend fun resolveRoster(
        region: String,
        realm: String,
        name: String
    ): Pair<GuildRoster, List<WowEntityRequest>> {
        val roster = blizzardClient
            .getHardcoreGuildRoster(region, realm, name)
            .orThrow("getHardcoreGuildRoster")

        val memberReqs = roster.members
            .asSequence()
            .filter { it.level >= MIN_MEMBER_LEVEL }
            .map { WowEntityRequest(it.name.lowercase(), region, it.realmSlug ?: realm) }
            .toList()

        return Pair(roster, memberReqs)
    }
}
