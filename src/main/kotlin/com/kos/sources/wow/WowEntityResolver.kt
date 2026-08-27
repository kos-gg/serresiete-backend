package com.kos.sources.wow

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.fx.coroutines.parMap
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.collect
import com.kos.common.error.NotCompetitiveCharacter
import com.kos.common.error.ServiceError
import com.kos.common.split
import com.kos.entities.EntityResolver
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import com.kos.views.WowExtraArguments
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class WowEntityResolver(
    private val repo: EntitiesRepository,
    private val raiderioClient: RaiderIoClient,
    private val blizzardClient: BlizzardClient
) : EntityResolver {
    override val game: Game = Game.WOW

    companion object {
        private const val MAX_CHARACTER_LEVEL = 90
        private const val MINIMUM_COMPETITIVE_SCORE = 1000.0
    }

    override suspend fun resolve(
        requested: List<EntityRequest>,
        extra: ViewExtraArguments?
    ): Either<ServiceError, ResolvedEntities> = either {
        val args = extra as? WowExtraArguments
        val (effectiveRequests, guildPayload) = if (args?.isGuild == true) {
            val guildReq = requested.first() as WowEntityRequest

            val (guildResponse, roster) = resolveRoster(guildReq.region, guildReq.realm, guildReq.name).bind()

            Pair(
                roster,
                GuildPayload(
                    name = guildReq.name,
                    realm = guildReq.realm,
                    region = guildReq.region,
                    blizzardId = guildResponse.guild.id
                )
            )
        } else {
            Pair(requested, null)
        }

        val (existing, newRequests) = getCurrentAndNewEntities(repo, effectiveRequests, Game.WOW)

        val (entities, unchecked) = if (args?.isGuild == true) {
            resolveGuildMembers(newRequests)
        } else {
            resolveIndividualCharacters(newRequests)
        }

        ResolvedEntities(
            entities = entities,
            existing = existing.map { it.value to it.alias },
            unchecked = unchecked,
            guild = guildPayload
        )
    }

    suspend fun resolveRoster(
        region: String,
        realm: String,
        name: String
    ): Either<ServiceError, Pair<GetWowRosterResponse, List<WowEntityRequest>>> = either {
        val roster = blizzardClient.getRetailGuildRoster(region, realm, name)
            .mapLeft { it.toSyncProcessingError("GetRetailGuildRoster") }
            .bind()

        val memberReqs = roster.members
            .asSequence()
            .filter { it.character.level >= MAX_CHARACTER_LEVEL }
            .map { WowEntityRequest(it.character.name, region, realm) }
            .toList()

        Pair(roster, memberReqs)
    }

    suspend fun resolveGuildMembers(
        newRequests: List<EntityRequest>
    ): Pair<List<Pair<InsertEntityRequest, String?>>, List<Pair<EntityRequest, ServiceError>>> {
        val (errors, oks) = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                either {
                    val score = raiderioClient.getScore(req)
                        .mapLeft { it.toSyncProcessingError("raiderIoScore") }
                        .bind()

                    ensure(score >= MINIMUM_COMPETITIVE_SCORE) { NotCompetitiveCharacter(req) }

                    req to req.alias
                }.mapLeft { req to it }
            }
            .toList()
            .split()

        return oks to errors
    }

    private suspend fun resolveIndividualCharacters(
        newRequests: List<EntityRequest>
    ): Pair<List<Pair<InsertEntityRequest, String?>>, List<Pair<EntityRequest, ServiceError>>> {
        val (unchecked, checked) = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                raiderioClient.exists(req).fold(
                    ifLeft = { error -> Either.Left(req to error.toSyncProcessingError("raiderIoExists")) },
                    ifRight = { exists -> Either.Right(req to exists) }
                )
            }
            .toList()
            .split()

        val entities = checked.collect(
            filter = { it.second },
            map = { it.first to it.first.alias }
        )

        return entities to unchecked
    }
}
