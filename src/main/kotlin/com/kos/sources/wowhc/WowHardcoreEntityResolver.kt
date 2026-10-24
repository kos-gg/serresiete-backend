package com.kos.sources.wowhc

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parMap
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
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

class WowHardcoreEntityResolver(
    private val repo: EntitiesRepository,
    private val blizzardClient: BlizzardClient
) : EntityResolver, WithLogger("WowHardcoreResolver") {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override suspend fun resolve(
        requested: List<EntityRequest>
    ): Either<ServiceError, ResolvedEntities> = either {
        val (existing, newRequests) = repo.getCurrentAndNewEntities(requested, Game.WOW_HC)

        val (unchecked, checked) = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                either {
                    blizzardClient.getClassicProfile(req.region, req.realm, req.name)
                        .mapLeft { req to it.toSyncProcessingError("getClassicProfile") }
                        .bind()
                        ?.let { profile ->
                            blizzardClient.getRealm(req.region, profile.realmId)
                                .mapLeft { req to it.toSyncProcessingError("getRealm") }
                                .bind()
                                .takeIf { it.isHardcore }
                                ?.let { WowEntityRequest(req.name, req.region, req.realm, profile.id) to req.alias }
                        }
                }
            }
            .toList()
            .split()

        ResolvedEntities(
            entities = checked.filterNotNull(),
            existing = existing.map { it.value to it.alias },
            unchecked = unchecked,
            guild = null
        )
    }
}