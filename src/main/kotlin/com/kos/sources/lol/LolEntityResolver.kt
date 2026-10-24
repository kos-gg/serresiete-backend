package com.kos.sources.lol

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parMap
import com.kos.clients.riot.RiotClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
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

class LolEntityResolver(
    private val repo: EntitiesRepository,
    private val riotClient: RiotClient
) : EntityResolver, WithLogger("LolResolver") {
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override suspend fun resolve(
        requested: List<EntityRequest>
    ): Either<ServiceError, ResolvedEntities> = either {
        val (existing, newRequests) = repo.getCurrentAndNewEntities(requested, Game.LOL)

        val (unchecked, checked) = newRequests.asFlow()
            .parMap(10) { req ->
                req as LolEntityRequest
                either {
                    riotClient.getPUUIDByRiotId(req.name, req.tag)
                        .mapLeft { req to it.toSyncProcessingError("getPUUIDByRiotId") }
                        .bind()
                        ?.let { puuid ->
                            riotClient.getSummonerByPuuid(puuid)
                                .mapLeft { req to it.toSyncProcessingError("getSummonerByPuuid") }
                                .bind()
                        }
                        ?.let { summoner ->
                            LolEnrichedEntityRequest(
                                req.name,
                                req.tag,
                                summoner.puuid,
                                summoner.profileIconId,
                                summoner.summonerLevel
                            ) to req.alias
                        }
                }
            }
            .toList()
            .split()

        val validated = checked
            .filterNotNull()
            .filterNot { (insertReq, _) -> repo.get(insertReq, Game.LOL).isDefined() }

        ResolvedEntities(
            entities = validated,
            existing = existing.map { it.value to it.alias },
            unchecked = unchecked,
            guild = null
        )
    }
}