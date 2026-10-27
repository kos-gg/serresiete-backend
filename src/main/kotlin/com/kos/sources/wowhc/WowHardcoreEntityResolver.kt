package com.kos.sources.wowhc

import arrow.core.raise.either
import arrow.core.getOrElse
import arrow.fx.coroutines.parMap
import com.kos.clients.NotFound
import com.kos.clients.blizzard.BlizzardClient
import com.kos.common.WithLogger
import com.kos.common.split
import com.kos.entities.EntityResolver
import com.kos.entities.domain.EntityRequest
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
class WowHardcoreEntityResolver(
    private val repo: EntitiesRepository,
    private val blizzardClient: BlizzardClient
) : EntityResolver, WithLogger("WowHardcoreResolver") {

    override suspend fun resolve(requested: List<EntityRequest>): ResolvedEntities {
        val (existing, newRequests) = repo.getCurrentAndNewEntities(requested, Game.WOW_HC)

        val (unchecked, checked) = newRequests.asFlow()
            .parMap(10) { req ->
                req as WowEntityRequest
                either {
                    val profile = blizzardClient.getClassicProfile(req.region, req.realm, req.name)
                        .getOrElse { if (it is NotFound) return@either null else raise(req to it) }
                    val realm = blizzardClient.getRealm(req.region, profile.realmId)
                        .mapLeft { req to it }
                        .bind()

                    if (realm.isHardcore) WowEntityRequest(req.name, req.region, req.realm, profile.id) to req.alias
                    else null
                }
            }
            .toList()
            .split()

        return ResolvedEntities(
            entities = checked.filterNotNull(),
            existing = existing.map { it.value to it.alias },
            unchecked = unchecked,
            guild = null
        )
    }
}