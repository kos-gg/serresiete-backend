package com.kos.sources.wow

import arrow.core.Either
import arrow.fx.coroutines.parMap
import com.kos.clients.ClientError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.orThrow
import com.kos.common.WithLogger
import com.kos.common.split
import com.kos.entities.EntityResolver
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.getCurrentAndNewEntities
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonRepository
import com.kos.views.Game
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WowGuildResolver(
    private val repo: EntitiesRepository,
    private val blizzardClient: BlizzardClient,
    private val wowSeasonRepository: WowSeasonRepository
) : EntityResolver, WithLogger("WowGuildResolver") {
    companion object {
        private const val MAX_CHARACTER_LEVEL = 90
        private const val SCORE_CUTOFF = 1000
    }

    override suspend fun resolve(requested: List<EntityRequest>): ResolvedEntities {
        val guildReq = requested.first() as WowEntityRequest
        logger.info("Resolving guild ${guildReq.name} (${guildReq.region}/${guildReq.realm})")

        val (guildRoster, members) = resolveRoster(guildReq.region, guildReq.realm, guildReq.name)
        val (existing, newRequests) = repo.getCurrentAndNewEntities(members, Game.WOW)

        return ResolvedEntities(
            entities = resolveMembers(newRequests),
            existing = existing.map { it.value to it.alias },
            unchecked = emptyList(),
            guild = payload(guildRoster, guildReq)
        )
    }

    suspend fun exists(guild: WowEntityRequest): Either<ClientError, GuildPayload?> =
        blizzardClient.getRetailGuildRoster(guild.region, guild.realm, guild.name)
            .map { roster -> roster?.let { payload(it, guild) } }

    suspend fun resolveMembers(newRequests: List<EntityRequest>): List<Pair<InsertEntityRequest, String?>> {
        val seasonId = checkNotNull(wowSeasonRepository.getCurrentSeason()?.id) { "There is no current mythic+ season" }

        val (failed, scored) = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                blizzardClient.getMythicKeystoneProfile(req.region, req.realm, req.name, seasonId)
                    .map { req to it.rating }
            }
            .toList()
            .split()

        val (competitive, belowScore) = scored.partition { (_, score) -> score >= SCORE_CUTOFF }

        logger.info(
            "Resolved ${newRequests.size} new guild members: ${competitive.size} kept, " +
                    "${belowScore.size} below minimum mythic+ score, ${failed.size} failed"
        )

        return competitive.map { (req, _) -> req to req.alias }
    }

    private fun payload(guildRoster: GuildRoster, guild: WowEntityRequest): GuildPayload =
        GuildPayload(
            guild.name.lowercase(),
            guild.realm.lowercase(),
            guild.region.lowercase(),
            guildRoster.guildId
        )

    suspend fun resolveRoster(
        region: String,
        realm: String,
        name: String
    ): Pair<GuildRoster, List<WowEntityRequest>> {
        val roster = checkNotNull(blizzardClient.getRetailGuildRoster(region, realm, name).orThrow()) {
            "Guild $name ($region/$realm) not found"
        }

        val members = roster.members
            .asSequence()
            .filter { it.level >= MAX_CHARACTER_LEVEL }
            .map {
                WowEntityRequest(
                    it.name,
                    region,
                    it.realmSlug ?: realm,
                    it.id
                )
            }
            .toList()

        return Pair(roster, members)
    }
}
