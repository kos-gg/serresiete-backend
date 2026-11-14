package com.kos.sources.wowhc

import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.orThrow
import com.kos.entities.GuildMembers
import com.kos.entities.GuildResolver
import com.kos.entities.domain.Entity
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.GuildRoster
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.domain.WowRealmGuild
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game

class WowHardcoreGuildResolver(
    override val entitiesRepository: EntitiesRepository,
    private val blizzardClient: BlizzardClient,
    private val characterResolver: WowHardcoreEntityResolver
) : GuildResolver<WowRealmGuild, WowEntityRequest> {
    companion object {
        private const val MIN_MEMBER_LEVEL = 10
    }

    override val game = Game.WOW_HC

    override suspend fun fetchRoster(guild: EntityRequest): GuildMembers<WowRealmGuild, WowEntityRequest> {
        val guildReq = guild as WowEntityRequest
        val (guildRoster, members) = resolveRoster(guildReq.region, guildReq.realm, guildReq.name)
        return GuildMembers(payload(guildRoster, guildReq), members)
    }

    override suspend fun admit(newMembers: List<WowEntityRequest>): List<Pair<InsertEntityRequest, String?>> =
        characterResolver.resolve(newMembers).entities

    override fun hasChanged(member: WowEntityRequest, tracked: Entity): Boolean = false

    private fun payload(guildRoster: GuildRoster, guild: WowEntityRequest): WowRealmGuild =
        WowRealmGuild(
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
            .orThrow()

        val memberReqs = roster.members
            .asSequence()
            .filter { it.level >= MIN_MEMBER_LEVEL }
            .map { WowEntityRequest(it.name.lowercase(), region, it.realmSlug ?: realm) }
            .toList()

        return Pair(roster, memberReqs)
    }
}
