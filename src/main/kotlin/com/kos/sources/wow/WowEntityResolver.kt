package com.kos.sources.wow

import arrow.core.recover
import arrow.fx.coroutines.parMap
import com.kos.clients.ClientError
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
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
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
    ): Pair<List<Pair<InsertEntityRequest, String?>>, List<Pair<EntityRequest, ClientError>>> {
        val (unchecked, checked) = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                blizzardClient.getRetailProfile(req.region, req.realm, req.name)
                    .map<Pair<WowEntityRequest, Long?>> { profile -> req to profile.id }
                    .recover { error -> if (error is NotFound) req to null else raise(req to error) }
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
