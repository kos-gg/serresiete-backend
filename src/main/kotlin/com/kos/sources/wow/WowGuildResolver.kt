package com.kos.sources.wow

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import arrow.fx.coroutines.parMap
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
import com.kos.common.error.NoCurrentMythicPlusSeason
import com.kos.common.error.NotCompetitiveCharacter
import com.kos.common.error.ServiceError
import com.kos.common.error.SyncProcessingError
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

    override suspend fun resolve(
        requested: List<EntityRequest>
    ): Either<ServiceError, ResolvedEntities> = either {
        val guildReq = requested.first() as WowEntityRequest
        logger.info("Resolving guild ${guildReq.name} (${guildReq.region}/${guildReq.realm})")

        val (guildRoster, members) = resolveRoster(guildReq.region, guildReq.realm, guildReq.name).bind()
        val (existing, newRequests) = repo.getCurrentAndNewEntities(members, Game.WOW)

        ResolvedEntities(
            entities = resolveMembers(newRequests).bind(),
            existing = existing.map { it.value to it.alias },
            unchecked = emptyList(),
            guild = payload(guildRoster, guildReq)
        )
    }

    suspend fun exists(guild: WowEntityRequest): Either<ServiceError, GuildPayload?> =
        blizzardClient.getRetailGuildRoster(guild.region, guild.realm, guild.name)
            .map { roster -> roster?.let { payload(it, guild) } }
            .mapLeft { it.toSyncProcessingError("GetRetailGuildRoster") }

    suspend fun resolveMembers(
        newRequests: List<EntityRequest>
    ): Either<ServiceError, List<Pair<InsertEntityRequest, String?>>> = either {
        val seasonId = ensureNotNull(wowSeasonRepository.getCurrentSeason()?.id) { NoCurrentMythicPlusSeason }

        val (errors, resolved) = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                either {
                    val score = blizzardClient.getMythicKeystoneProfile(req.region, req.realm, req.name, seasonId)
                        .map { it.rating }
                        .mapLeft { it.toSyncProcessingError("getMythicKeystoneProfile") }
                        .bind()

                    ensure(score >= SCORE_CUTOFF) { NotCompetitiveCharacter(req) }

                    req to req.alias
                }.mapLeft { req to it }
            }
            .toList()
            .split()

        val (belowScore, failed) = errors.partition { (_, error) -> error is NotCompetitiveCharacter }

        logger.info(
            "Resolved ${newRequests.size} new guild members: ${resolved.size} kept, " +
                "${belowScore.size} below minimum mythic+ score, ${failed.size} failed"
        )

        resolved
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
    ): Either<ServiceError, Pair<GuildRoster, List<WowEntityRequest>>> = either {
        val roster = ensureNotNull(
            blizzardClient.getRetailGuildRoster(region, realm, name)
                .mapLeft { it.toSyncProcessingError("GetRetailGuildRoster") }
                .bind()
        ) { SyncProcessingError("NOT_FOUND", "Guild $name ($region/$realm) not found") }

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

        Pair(roster, members)
    }
}
