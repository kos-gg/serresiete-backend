package com.kos.entities.entitiesResolvers

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.common.HttpError
import com.kos.common.NonHardcoreCharacter
import com.kos.common.WithLogger
import com.kos.common.split
import com.kos.entities.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.ViewExtraArguments
import com.kos.views.WowHardcoreExtraArguments
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

class WowHardcoreResolver(
    private val repo: EntitiesRepository,
    private val blizzardClient: BlizzardClient
) : EntityResolver, WithLogger("WowHardcoreResolver") {

    override suspend fun resolve(
        requested: List<CreateEntityRequest>,
        extra: ViewExtraArguments?
    ): Either<HttpError, ResolvedEntities> = either {
        val args = extra as? WowHardcoreExtraArguments
        val (effectiveRequests, guildPayload) = if (args?.isGuild == true) {
            val guildReq = requested.first() as WowEntityRequest

            val (guildResponse, roster) = resolveRoster(guildReq.region, guildReq.realm, guildReq.name).bind()

            Pair(
                roster,
                GuildPayload(
                    name = guildReq.name,
                    realm = guildReq.realm,
                    region = guildReq.region,
                    blizzardId = guildResponse.guild.id
                )
            )
        } else {
            Pair(requested, null)
        }

        val (existing, newRequests) = getCurrentAndNewEntities(repo, effectiveRequests, Game.WOW_HC)

        val validatedTuples: List<Pair<InsertEntityRequest, String?>> = coroutineScope {
            val (errors, oks) = newRequests.map { req ->
                async {
                    either {
                        req as WowEntityRequest
                        val profile = blizzardClient.getCharacterProfile(
                            req.region, req.realm, req.name
                        ).bind()

                        val realm = blizzardClient.getRealm(req.region, profile.realm.id).bind()

                        ensure(
                            realm.category == "Hardcore" || realm.category == "Anniversary"
                        ) { NonHardcoreCharacter(req) }

                        WowEnrichedEntityRequest(
                            req.name,
                            req.region,
                            req.realm,
                            profile.id
                        ) to req.alias
                    }
                }
            }.awaitAll().split()

            errors.forEach { logger.error(it.error()) }
            oks
        }

        ResolvedEntities(
            entities = validatedTuples,
            existing = existing.map { it.value to it.alias },
            guild = guildPayload
        )
    }

    suspend fun resolveRoster(
        region: String,
        realm: String,
        name: String
    ): Either<HttpError, Pair<GetWowRosterResponse, List<WowEntityRequest>>> {
        return either {
            val rosterEither = blizzardClient.getGuildRoster(region, realm, name)
            val roster = rosterEither.bind()

            val memberReqs = roster.members
                .asSequence()
                .filter { it.character.level >= 10 }
                .map { m -> WowEntityRequest(m.character.name.lowercase(), region, realm) }
                .toList()
            Pair(roster, memberReqs)
        }
    }
}