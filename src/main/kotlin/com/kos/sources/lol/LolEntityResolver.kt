package com.kos.sources.lol

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parMap
import com.kos.clients.riot.RiotClient
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.isDefined
import com.kos.entities.EntityResolver
import com.kos.entities.domain.CreateEntityRequest
import com.kos.entities.domain.LolEnrichedEntityRequest
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.domain.ResolvedEntities
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.filterNot
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.toList

class LolEntityResolver(
    private val repo: EntitiesRepository,
    private val riotClient: RiotClient
) : EntityResolver, WithLogger("LolResolver") {
    override val game: Game = Game.LOL

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override suspend fun resolve(
        requested: List<CreateEntityRequest>,
        extra: ViewExtraArguments?
    ): Either<ServiceError, ResolvedEntities> = either {
        val (existing, newRequests) = getCurrentAndNewEntities(repo, requested, Game.LOL)

        val validated = newRequests.asFlow()
            .parMap(10) { req ->
                either {
                    req as LolEntityRequest

                    val puuid = riotClient.getPUUIDByRiotId(req.name, req.tag)
                        .onLeft { logger.error(it.toString()) }
                        .bind()

                    val summoner = riotClient.getSummonerByPuuid(puuid.puuid)
                        .onLeft { logger.error(it.toString()) }
                        .bind()

                    LolEnrichedEntityRequest(
                        req.name,
                        req.tag,
                        summoner.puuid,
                        summoner.profileIconId,
                        summoner.summonerLevel
                    ) to req.alias

                }.getOrNull()
            }
            .filterNotNull()
            .filterNot { (insertReq, _) ->
                repo.get(insertReq, Game.LOL).isDefined()
            }
            .toList()

        ResolvedEntities(
            entities = validated,
            existing = existing.map { it.value to it.alias },
            guild = null
        )
    }
}