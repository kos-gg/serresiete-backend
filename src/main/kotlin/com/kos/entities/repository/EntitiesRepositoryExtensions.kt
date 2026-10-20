package com.kos.entities.repository

import arrow.core.Either
import arrow.fx.coroutines.parMap
import com.kos.common.split
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.EntityWithAlias
import com.kos.views.Game
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
suspend fun EntitiesRepository.getCurrentAndNewEntities(
    entities: List<EntityRequest>,
    game: Game
): Pair<List<EntityWithAlias>, List<EntityRequest>> =
    entities.asFlow()
        .parMap(3) { req ->
            val existing = get(req, game)
            if (existing == null) Either.Right(req)
            else Either.Left(EntityWithAlias(existing, req.alias))
        }
        .toList()
        .split()
