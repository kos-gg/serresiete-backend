package com.kos.sources.wowforever

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
import com.kos.entities.domain.WowForeverEntityRequest
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.getCurrentAndNewEntities
import com.kos.views.Game
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class)
class WowForeverEntityResolver(
    private val repo: EntitiesRepository,
    private val blizzardClient: BlizzardClient
) : EntityResolver, WithLogger("WowForeverEntityResolver") {

    override suspend fun resolve(requested: List<EntityRequest>): ResolvedEntities {
        val (existing, newRequests) = repo.getCurrentAndNewEntities(requested, Game.WOW_FOREVER)

        val (unchecked, checked) = newRequests.asFlow()
            .parMapUnordered(10) { req -> resolveCharacter(req as WowForeverEntityRequest) }
            .toList()
            .split()

        return ResolvedEntities(
            entities = checked.filterNotNull(),
            existing = existing.map { it.value to it.alias },
            unchecked = unchecked
        )
    }

    private suspend fun resolveCharacter(
        req: WowForeverEntityRequest
    ): Either<EntityRequest, Pair<WowForeverEntityRequest, String?>?> =
        either {
            recover({
                val profile = blizzardClient.getForeverProfile(req.region, req.name, req.surname).bind()
                req.copy(blizzardId = profile.id) to req.alias
            }) { error ->
                if (error is NotFound) null
                else {
                    logger.warn("Couldn't check whether $req exists: $error")
                    raise(req)
                }
            }
        }
}
