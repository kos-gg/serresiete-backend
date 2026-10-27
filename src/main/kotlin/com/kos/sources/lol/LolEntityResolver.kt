package com.kos.sources.lol

import arrow.core.raise.either
import arrow.core.getOrElse
import arrow.fx.coroutines.parMap
import com.kos.clients.NotFound
import com.kos.clients.riot.RiotClient
import com.kos.common.WithLogger
import com.kos.common.isDefined
import com.kos.common.split
import com.kos.entities.EntityResolver
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.LolEnrichedEntityRequest
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.domain.ResolvedEntities
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.getCurrentAndNewEntities
import com.kos.views.Game
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
class LolEntityResolver(
    private val repo: EntitiesRepository,
    private val riotClient: RiotClient
) : EntityResolver, WithLogger("LolResolver") {

    override suspend fun resolve(requested: List<EntityRequest>): ResolvedEntities {
        val (existing, newRequests) = repo.getCurrentAndNewEntities(requested, Game.LOL)

        val (unchecked, checked) = newRequests.asFlow()
            .parMap(10) { req ->
                req as LolEntityRequest
                either {
                    val puuid = riotClient.getPUUIDByRiotId(req.name, req.tag)
                        .getOrElse { if (it is NotFound) return@either null else raise(req to it) }
                    val summoner = riotClient.getSummonerByPuuid(puuid)
                        .mapLeft { req to it }
                        .bind()

                    LolEnrichedEntityRequest(
                        req.name,
                        req.tag,
                        summoner.puuid,
                        summoner.profileIconId,
                        summoner.summonerLevel
                    ) to req.alias
                }
            }
            .toList()
            .split()

        val validated = checked
            .filterNotNull()
            .filterNot { (insertReq, _) -> repo.get(insertReq, Game.LOL).isDefined() }

        return ResolvedEntities(
            entities = validated,
            existing = existing.map { it.value to it.alias },
            unchecked = unchecked,
            guild = null
        )
    }
}