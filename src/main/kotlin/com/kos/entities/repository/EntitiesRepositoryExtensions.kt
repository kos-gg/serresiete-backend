package com.kos.entities.repository

import arrow.fx.coroutines.parMapUnordered
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.EntityWithAlias
import com.kos.views.Game
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class)
suspend fun EntitiesRepository.getCurrentAndNewEntities(
    entities: List<EntityRequest>,
    game: Game
): Pair<List<EntityWithAlias>, List<EntityRequest>> =
    entities.asFlow()
        .parMapUnordered(3) { req -> req to get(req, game) }
        .toList()
        .let { lookups ->
            lookups.mapNotNull { (req, entity) -> entity?.let { EntityWithAlias(it, req.alias) } } to
                    lookups.filter { (_, entity) -> entity == null }.map { (req, _) -> req }
        }
