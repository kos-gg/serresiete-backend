package com.kos.sources.wowhc

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.recover
import arrow.fx.coroutines.parMapUnordered
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class)
class WowHardcoreEntityResolver(
    private val repo: EntitiesRepository,
    private val blizzardClient: BlizzardClient
) : EntityResolver, WithLogger("WowHardcoreResolver") {

    override suspend fun resolve(requested: List<EntityRequest>): ResolvedEntities {
        val (existing, newRequests) = repo.getCurrentAndNewEntities(requested, Game.WOW_HC)

        val (unchecked, checked) = newRequests.asFlow()
            .parMapUnordered(10) { req -> resolveCharacter(req as WowEntityRequest) }
            .toList()
            .split()

        return ResolvedEntities(
            entities = checked.filterNotNull(),
            existing = existing.map { it.value to it.alias },
            unchecked = unchecked
        )
    }

    private suspend fun resolveCharacter(
        req: WowEntityRequest
    ): Either<EntityRequest, Pair<WowEntityRequest, String?>?> =
        either {
            recover({
                val profile = blizzardClient.getClassicProfile(req.region, req.realm, req.name).bind()
                val realm = blizzardClient.getRealm(req.region, profile.realmId).bind()
                if (realm.isHardcore) WowEntityRequest(req.name, req.region, req.realm, profile.id) to req.alias
                else null
            }) { error ->
                if (error is NotFound) null
                else {
                    logger.warn("Couldn't check whether $req exists: $error")
                    raise(req)
                }
            }
        }
}