package com.kos.sources.wow

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parMap
import com.kos.clients.HttpError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
import com.kos.common.collect
import com.kos.common.error.ServiceError
import com.kos.common.split
import com.kos.entities.EntityResolver
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.getCurrentAndNewEntities
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
    private val blizzardClient: BlizzardClient,
    private val guildResolver: WowGuildResolver
) : EntityResolver, WithLogger("WowEntityResolver") {
    override val game: Game = Game.WOW

    override suspend fun resolve(
        requested: List<EntityRequest>,
        extra: ViewExtraArguments?
    ): Either<ServiceError, ResolvedEntities> = either {
        val isGuild = (extra as? WowExtraArguments)?.isGuild == true

        if (!isGuild) {
            val (existing, newRequests) = repo.getCurrentAndNewEntities(requested, Game.WOW)
            val (entities, unchecked) = resolveCharacters(newRequests)
            ResolvedEntities(
                entities = entities,
                existing = existing.map { it.value to it.alias },
                unchecked = unchecked,
                guild = null
            )
        } else {
            val guildReq = requested.first() as WowEntityRequest
            val (guildRoster, members) = guildResolver.resolveRoster(guildReq.region, guildReq.realm, guildReq.name).bind()
            val (existing, newRequests) = repo.getCurrentAndNewEntities(members, Game.WOW)

            ResolvedEntities(
                entities = guildResolver.resolveMembers(newRequests),
                existing = existing.map { it.value to it.alias },
                unchecked = emptyList(),
                guild = guildResolver.payload(guildRoster, guildReq)
            )
        }
    }

    private suspend fun resolveCharacters(
        newRequests: List<EntityRequest>
    ): Pair<List<Pair<InsertEntityRequest, String?>>, List<Pair<EntityRequest, ServiceError>>> {
        val (unchecked, checked) = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                blizzardClient.getRetailProfile(req.region, req.realm, req.name).fold(
                    ifLeft = { error ->
                        if (error is HttpError && error.status == 404) Either.Right(req to null)
                        else Either.Left(req to error.toSyncProcessingError("getRetailProfile"))
                    },
                    ifRight = { profile -> Either.Right(req to profile.id) }
                )
            }
            .toList()
            .split()

        val entities = checked.collect(
            filter = { it.second != null },
            map = { it.first.copy(blizzardId = it.second) to it.first.alias }
        )

        return entities to unchecked
    }
}
