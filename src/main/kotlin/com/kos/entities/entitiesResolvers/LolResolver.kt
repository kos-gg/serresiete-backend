package com.kos.entities.entitiesResolvers

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.ClientError
import com.kos.clients.riot.RiotClient
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.isDefined
import com.kos.entities.CreateEntityRequest
import com.kos.entities.LolEnrichedEntityRequest
import com.kos.entities.LolEntityRequest
import com.kos.entities.ResolvedEntities
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*

class LolResolver(
    private val repo: EntitiesRepository,
    private val riotClient: RiotClient
) : EntityResolver, WithLogger("LolResolver") {

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