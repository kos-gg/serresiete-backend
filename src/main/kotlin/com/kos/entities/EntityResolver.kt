package com.kos.entities

import arrow.core.Either
import arrow.fx.coroutines.parMap
import com.kos.common.error.ServiceError
import com.kos.common.split
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.EntityWithAlias
import com.kos.entities.domain.ResolvedEntities
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

interface EntityResolver {
    val game: Game

    suspend fun resolve(
        requested: List<EntityRequest>,
        extra: ViewExtraArguments?
    ): Either<ServiceError, ResolvedEntities>

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    suspend fun getCurrentAndNewEntities(
        repo: EntitiesRepository,
        entities: List<EntityRequest>,
        game: Game
    ): Pair<List<EntityWithAlias>, List<EntityRequest>> {
        return entities.asFlow()
            .parMap(3) { req ->
                val existing = repo.get(req, game)
                if (existing == null) Either.Right(req)
                else Either.Left(EntityWithAlias(existing, req.alias))
            }
            .toList()
            .split()
    }
}