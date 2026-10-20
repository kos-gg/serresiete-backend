package com.kos.sources.wowhc

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.error.ServiceError
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

    override suspend fun resolve(
        requested: List<EntityRequest>
    ): Either<ServiceError, ResolvedEntities> = either {
        val guildReq = requested.first() as WowEntityRequest
        val (guildRoster, members) = resolveRoster(guildReq.region, guildReq.realm, guildReq.name).bind()

        characterResolver.resolve(members).bind().copy(guild = payload(guildRoster, guildReq))
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
    ): Either<ServiceError, Pair<GuildRoster, List<WowEntityRequest>>> = either {
        val roster = blizzardClient
            .getHardcoreGuildRoster(region, realm, name)
            .mapLeft { it.toSyncProcessingError("GetHardcoreGuildRoster") }
            .bind()

        val memberReqs = roster.members
            .asSequence()
            .filter { it.level >= MIN_MEMBER_LEVEL }
            .map { WowEntityRequest(it.name.lowercase(), region, it.realmSlug ?: realm) }
            .toList()

        Pair(roster, memberReqs)
    }
}
