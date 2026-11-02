package com.kos.sources.wow

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.recover
import arrow.fx.coroutines.parMapUnordered
import com.kos.clients.NotFound
import com.kos.clients.blizzard.BlizzardClient
import com.kos.common.WithLogger
import com.kos.common.collect
import com.kos.common.split
import com.kos.entities.EntityResolver
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest
import com.kos.entities.domain.ResolvedEntities
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.getCurrentAndNewEntities
import com.kos.views.Game
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class)
class WowEntityResolver(
    private val repo: EntitiesRepository,
    private val blizzardClient: BlizzardClient
) : EntityResolver, WithLogger("WowEntityResolver") {

    override suspend fun resolve(requested: List<EntityRequest>): ResolvedEntities {
        val (existing, newRequests) = repo.getCurrentAndNewEntities(requested, Game.WOW)
        val (entities, unchecked) = resolveCharacters(newRequests)
        return ResolvedEntities(
            entities = entities,
            existing = existing.map { it.value to it.alias },
            unchecked = unchecked,
            guild = null
        )
    }

    private suspend fun resolveCharacters(
        newRequests: List<EntityRequest>
    ): Pair<List<Pair<InsertEntityRequest, String?>>, List<EntityRequest>> {
        val (unchecked, checked) = newRequests.asFlow()
            .parMapUnordered(10) { req -> resolveCharacter(req as WowEntityRequest) }
            .toList()
            .split()

        val entities = checked.collect(
            filter = { it.second != null },
            map = { it.first.copy(blizzardId = it.second) to it.first.alias }
        )

        return entities to unchecked
    }

    private suspend fun resolveCharacter(
        req: WowEntityRequest
    ): Either<EntityRequest, Pair<WowEntityRequest, Long?>> =
        either {
            recover({ req to blizzardClient.getRetailProfile(req.region, req.realm, req.name).bind().id }) { error ->
                if (error is NotFound) req to null
                else {
                    logger.warn("Couldn't check whether $req exists: $error")
                    raise(req)
                }
            }
        }
}
