package com.kos.entities.repository

import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import arrow.fx.coroutines.parMapUnordered
import com.kos.common.split
import com.kos.entities.domain.Entity
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.EntityWithAlias
import com.kos.entities.domain.InsertEntityRequest
import com.kos.views.Game
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class)
suspend fun EntitiesRepository.getCurrentAndNewEntities(
    entities: List<EntityRequest>,
    game: Game
): Pair<List<EntityRequest>, List<EntityWithAlias>> =
    entities.asFlow()
        .parMapUnordered(3) { req ->
            either { EntityWithAlias(ensureNotNull(get(req, game)) { req }, req.alias) }
        }
        .toList()
        .split()

@OptIn(FlowPreview::class)
suspend fun <M : InsertEntityRequest> EntitiesRepository.getCurrentAndNewMembers(
    members: List<M>,
    game: Game
): Pair<List<M>, List<Pair<M, Entity>>> =
    members.asFlow()
        .parMapUnordered(3) { member ->
            either { member to ensureNotNull(get(member, game)) { member } }
        }
        .toList()
        .split()
