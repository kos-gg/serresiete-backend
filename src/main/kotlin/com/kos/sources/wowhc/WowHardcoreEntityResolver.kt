package com.kos.sources.wowhc

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.fx.coroutines.parMap
import com.kos.clients.blizzard.BlizzardClient
import com.kos.common.WithLogger
import com.kos.common.error.NonHardcoreCharacter
import com.kos.common.error.ServiceError
import com.kos.common.split
import com.kos.entities.EntityResolver
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.getCurrentAndNewEntities
import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import com.kos.views.WowExtraArguments
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList

class WowHardcoreEntityResolver(
    private val repo: EntitiesRepository,
    private val blizzardClient: BlizzardClient,
    private val guildResolver: WowHardcoreGuildResolver
) : EntityResolver, WithLogger("WowHardcoreResolver") {
    override val game: Game = Game.WOW_HC

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override suspend fun resolve(
        requested: List<EntityRequest>,
        extra: ViewExtraArguments?
    ): Either<ServiceError, ResolvedEntities> = either {
        val args = extra as? WowExtraArguments
        val (effectiveRequests, guildPayload) = if (args?.isGuild == true) {
            val guildReq = requested.first() as WowEntityRequest

            val (guildRoster, members) = guildResolver.resolveRoster(guildReq.region, guildReq.realm, guildReq.name).bind()

            Pair(members, guildResolver.payload(guildRoster, guildReq))
        } else {
            Pair(requested, null)
        }

        val (existing, newRequests) = repo.getCurrentAndNewEntities(effectiveRequests, Game.WOW_HC)

        val (errors, oks) = newRequests.asFlow()
            .parMap(10) { req ->
                either {
                    req as WowEntityRequest
                    val profile = blizzardClient.getClassicProfile(
                        req.region, req.realm, req.name
                    ).bind()

                    val realm = blizzardClient.getRealm(req.region, profile.realmId).bind()

                    ensure(realm.isHardcore) { NonHardcoreCharacter(req) }

                    WowEntityRequest(
                        req.name,
                        req.region,
                        req.realm,
                        profile.id
                    ) to req.alias
                }
            }
            .toList()
            .split()

        errors.forEach { logger.error(it.toString()) }

        val validatedTuples: List<Pair<InsertEntityRequest, String?>> = oks

        ResolvedEntities(
            entities = validatedTuples,
            existing = existing.map { it.value to it.alias },
            guild = guildPayload
        )
    }
}