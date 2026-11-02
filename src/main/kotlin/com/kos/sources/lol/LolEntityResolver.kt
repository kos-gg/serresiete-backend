package com.kos.sources.lol

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.recover
import arrow.fx.coroutines.parMapUnordered
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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

@OptIn(FlowPreview::class)
class LolEntityResolver(
    private val repo: EntitiesRepository,
    private val riotClient: RiotClient
) : EntityResolver, WithLogger("LolResolver") {

    override suspend fun resolve(requested: List<EntityRequest>): ResolvedEntities {
        val (existing, newRequests) = repo.getCurrentAndNewEntities(requested, Game.LOL)

        val (unchecked, checked) = newRequests.asFlow()
            .parMapUnordered(10) { req -> resolveCharacter(req as LolEntityRequest) }
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

    private suspend fun resolveCharacter(
        req: LolEntityRequest
    ): Either<EntityRequest, Pair<LolEnrichedEntityRequest, String?>?> =
        either {
            recover({
                val puuid = riotClient.getPUUIDByRiotId(req.name, req.tag).bind()
                val summoner = riotClient.getSummonerByPuuid(puuid).bind()
                LolEnrichedEntityRequest(
                    req.name,
                    req.tag,
                    summoner.puuid,
                    summoner.profileIconId,
                    summoner.summonerLevel
                ) to req.alias
            }) { error ->
                if (error is NotFound) null
                else {
                    logger.warn("Couldn't check whether $req exists: $error")
                    raise(req)
                }
            }
        }
}