package com.kos.sources.wow

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parMap
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.collect
import com.kos.common.error.ServiceError
import com.kos.entities.EntityResolver
import com.kos.entities.domain.CreateEntityRequest
import com.kos.entities.domain.ResolvedEntities
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

class WowEntityResolver(
    private val repo: EntitiesRepository,
    private val raiderioClient: RaiderIoClient
) : EntityResolver {
    override val game: Game = Game.WOW

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override suspend fun resolve(
        requested: List<CreateEntityRequest>,
        extra: ViewExtraArguments?
    ): Either<ServiceError, ResolvedEntities> = either {
        val (existing, newRequests) = getCurrentAndNewEntities(repo, requested, Game.WOW)

        val validated = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                val exists = raiderioClient.exists(req)
                req to exists
            }
            .toList()
            .collect(
                filter = { it.second },
                map = { it.first to it.first.alias }
            )

        ResolvedEntities(
            entities = validated,
            existing = existing.map { it.value to it.alias },
            guild = null
        )
    }
}