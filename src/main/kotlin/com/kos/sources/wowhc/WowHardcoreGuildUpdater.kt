package com.kos.sources.wowhc

import arrow.core.Either
import arrow.core.getOrElse
import com.kos.common.error.GuildUpdateFailed
import com.kos.common.error.ServiceError
import com.kos.common.split
import com.kos.entities.EntityUpdater
import com.kos.entities.domain.GuildPayload
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsRepository

class WowHardcoreGuildUpdater(
    private val guildResolver: WowHardcoreGuildResolver,
    private val resolver: WowHardcoreEntityResolver,
    private val entitiesRepository: EntitiesRepository,
    private val viewsRepository: ViewsRepository
) : EntityUpdater<Pair<GuildPayload, String>> {
    override suspend fun update(
        entities: List<Pair<GuildPayload, String>>
    ): List<ServiceError> {

        val initialResults = entities
            .map { (guild, viewId) ->
                guildResolver
                    .resolveRoster(guild.region, guild.realm, guild.name)
                    .map { (_, roster) -> Triple(guild, roster, viewId) }
            }

        val (initialErrors, rostersWithViewIds) = initialResults.split()

        val downstreamErrors = rostersWithViewIds.flatMap { (guild, requests, viewId) ->
            Either.catch {
                resolver.resolve(requests).fold(
                    ifLeft = { listOf(it) },

                    ifRight = { resolved ->
                        val inserted = entitiesRepository.insert(resolved.entities.map { it.first }, Game.WOW_HC)
                        val entitiesToAssociate =
                            inserted.map { it.id to null } +
                                    resolved.existing.map { it.first.id to it.second }

                        viewsRepository.associateEntitiesIdsToView(
                            entitiesToAssociate,
                            viewId
                        )

                        emptyList()
                    }
                )
            }.getOrElse { listOf(GuildUpdateFailed(guild, it)) }
        }

        return initialErrors + downstreamErrors
    }

}