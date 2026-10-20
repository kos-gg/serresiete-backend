package com.kos.sources.wow

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.fx.coroutines.parMap
import com.kos.clients.HttpError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
import com.kos.common.error.NotCompetitiveCharacter
import com.kos.common.error.ServiceError
import com.kos.common.split
import com.kos.entities.EntityResolver
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.getCurrentAndNewEntities
import com.kos.views.Game
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WowGuildResolver(
    private val repo: EntitiesRepository,
    private val raiderioClient: RaiderIoClient,
    private val blizzardClient: BlizzardClient
) : EntityResolver, WithLogger("WowGuildResolver") {
    override val game: Game = Game.WOW

    companion object {
        private const val MAX_CHARACTER_LEVEL = 90
        private const val SCORE_CUTOFF = 1000
    }

    override suspend fun resolve(
        requested: List<EntityRequest>
    ): Either<ServiceError, ResolvedEntities> = either {
        val guildReq = requested.first() as WowEntityRequest
        val (guildRoster, members) = resolveRoster(guildReq.region, guildReq.realm, guildReq.name).bind()
        val (existing, newRequests) = repo.getCurrentAndNewEntities(members, Game.WOW)

        ResolvedEntities(
            entities = resolveMembers(newRequests),
            existing = existing.map { it.value to it.alias },
            unchecked = emptyList(),
            guild = payload(guildRoster, guildReq)
        )
    }

    suspend fun exists(guild: WowEntityRequest): Either<ServiceError, GuildPayload?> =
        blizzardClient.getRetailGuildRoster(guild.region, guild.realm, guild.name).fold(
            ifLeft = { error ->
                if (error is HttpError && error.status == 404) Either.Right(null)
                else Either.Left(error.toSyncProcessingError("GetRetailGuildRoster"))
            },
            ifRight = { roster -> Either.Right(payload(roster, guild)) }
        )

    suspend fun resolveMembers(
        newRequests: List<EntityRequest>
    ): List<Pair<InsertEntityRequest, String?>> {
        val (errors, resolved) = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                either {
                    val score = raiderioClient.getScore(req)
                        .mapLeft { it.toSyncProcessingError("raiderIoScore") }
                        .bind()

                    ensure(score >= SCORE_CUTOFF) { NotCompetitiveCharacter(req) }

                    req to req.alias
                }.mapLeft { req to it }
            }
            .toList()
            .split()

        errors.forEach { (req, error) ->
            logger.warn("Skipping guild member ${req.name}-${req.realm}: ${error.error()}")
        }

        return resolved
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
        val roster = blizzardClient.getRetailGuildRoster(region, realm, name)
            .mapLeft { it.toSyncProcessingError("GetRetailGuildRoster") }
            .bind()

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
