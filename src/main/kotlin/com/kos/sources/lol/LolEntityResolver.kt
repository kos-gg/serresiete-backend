package com.kos.sources.lol

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.riot.RiotClient
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.isDefined
import com.kos.entities.domain.CreateEntityRequest
import com.kos.entities.EntityResolver
import com.kos.entities.domain.LolEnrichedEntityRequest
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.domain.ResolvedEntities
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*

class LolEntityResolver(
    private val repo: EntitiesRepository,
    private val riotClient: RiotClient
) : EntityResolver, WithLogger("LolResolver") {
    override val game: Game = Game.LOL

    override suspend fun resolve(
        requested: List<CreateEntityRequest>,
        extra: ViewExtraArguments?
    ): Either<ServiceError, ResolvedEntities> = either {
        val (existing, newRequests) = getCurrentAndNewEntities(repo, requested, Game.LOL)

        val validated = coroutineScope {
            newRequests.asFlow()
                .buffer(40)
                .mapNotNull { req ->
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
                .buffer(3)
                .filterNot { (insertReq, _) ->
                    repo.get(insertReq, Game.LOL).isDefined()
                }
                .toList()
        }

        ResolvedEntities(
            entities = validated,
            existing = existing.map { it.value to it.alias },
            guild = null
        )
    }
}