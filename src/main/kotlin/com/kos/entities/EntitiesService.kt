package com.kos.entities

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.recover
import com.kos.clients.ClientError
import com.kos.clients.NotFound
import com.kos.common.WithLogger
import com.kos.common.error.ThirdPartyUnavailable
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.wowguilds.WowGuildsRepository
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowGuildResolver
import com.kos.sources.wowforever.WowForeverGuildResolver
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.Game

data class EntitiesService(
    private val entitiesRepository: EntitiesRepository,
    private val wowGuildsRepository: WowGuildsRepository,
    private val entitiesResolverProvider: EntityResolverProvider,
    private val wowGuildResolver: WowGuildResolver,
    private val lolUpdater: LolEntityUpdater,
    private val wowHardcoreGuildUpdater: WowHardcoreGuildUpdater,
    private val wowGuildUpdater: GuildUpdater<WowRealmGuild, WowEntityRequest>,
    private val wowForeverGuildResolver: WowForeverGuildResolver,
    private val wowForeverGuildUpdater: GuildUpdater<WowForeverGuild, WowForeverEntityRequest>,
) : WithLogger("EntitiesService") {

    suspend fun exists(
        requestedEntities: List<EntityRequest>,
        game: Game
    ): EntitiesExistResponse {
        val resolved = resolveEntities(requestedEntities, game)
        val exist = (resolved.entities.map { it.first.toRequest() } + resolved.existing.map { it.first.toRequest() })
            .map { it.toResponse() }
        val unchecked = resolved.unchecked.map { it.toResponse() }
        val nonExisting = requestedEntities.map { it.toResponse() }.filterNot { it in exist || it in unchecked }

        return EntitiesExistResponse(
            exist = exist,
            nonExisting = nonExisting,
            unchecked = unchecked
        )
    }

    suspend fun wowGuildExists(name: String, region: String, realm: String): Either<ThirdPartyUnavailable, GuildExistsResponse> =
        guildExists(Game.WOW, name, realm, region) {
            wowGuildResolver.exists(WowEntityRequest(name, region, realm))
        }

    suspend fun wowForeverGuildExists(name: String, region: String): Either<ThirdPartyUnavailable, GuildExistsResponse> =
        guildExists(Game.WOW_FOREVER, name, null, region) {
            wowForeverGuildResolver.exists(WowForeverEntityRequest(name = name, region = region))
        }

    private suspend fun guildExists(
        game: Game,
        name: String,
        realm: String?,
        region: String,
        fetchGuild: suspend () -> Either<ClientError, WowGuild>
    ): Either<ThirdPartyUnavailable, GuildExistsResponse> =
        findTrackedGuild(name, realm, region, game)
            ?.let { (guild, _) -> Either.Right(GuildExistsResponse(guild)) }
            ?: either {
                recover({
                    GuildExistsResponse(fetchGuild().bind())
                }) {
                    if (it is NotFound) GuildExistsResponse(null)
                    else {
                        logger.warn("Couldn't check whether $game guild $name (${listOfNotNull(region, realm).joinToString("/")}) exists: $it")
                        raise(ThirdPartyUnavailable)
                    }
                }
            }

    suspend fun resolveEntities(requestedEntities: List<EntityRequest>, game: Game): ResolvedEntities =
        entitiesResolverProvider.resolverFor(game).resolve(requestedEntities)

    suspend fun resolveGuild(guild: EntityRequest, game: Game): ResolvedGuild<*, *> =
        entitiesResolverProvider.guildResolverFor(game).resolve(guild)

    suspend fun updateEntities(
        game: Game
    ): List<PartialFailure> {
        @Suppress("UNCHECKED_CAST")
        return when (game) {
            Game.LOL -> lolUpdater.update(entitiesRepository.get(game) as List<LolEntity>)
            Game.WOW -> listOf()
            Game.WOW_HC -> listOf()
            Game.WOW_FOREVER -> listOf()
        }
    }

    suspend fun updateWowHardcoreGuilds(): List<PartialFailure> {
        val guildsWithViewId = wowGuildsRepository.getRealmGuilds(Game.WOW_HC)
        return wowHardcoreGuildUpdater.update(guildsWithViewId)
    }

    suspend fun updateWowGuilds(): List<PartialFailure> {
        val guildsWithViewId = wowGuildsRepository.getRealmGuilds(Game.WOW)
        return wowGuildUpdater.update(guildsWithViewId)
    }

    suspend fun updateWowForeverGuilds(): List<PartialFailure> {
        val guildsWithViewId = wowGuildsRepository.getForeverGuilds()
        return wowForeverGuildUpdater.update(guildsWithViewId)
    }

    suspend fun get(id: Long, game: Game): Entity? = entitiesRepository.get(id, game)
    suspend fun get(game: Game): List<Entity> = entitiesRepository.get(game)

    suspend fun insert(entities: List<InsertEntityRequest>, game: Game) = entitiesRepository.insert(entities, game)
    suspend fun insertGuild(guild: WowGuild, viewId: String, game: Game) =
        wowGuildsRepository.insertGuild(guild, viewId, game)

    suspend fun findTrackedGuild(name: String, realm: String?, region: String, game: Game): Pair<WowGuild, String>? =
        wowGuildsRepository.findTrackedGuild(name, realm, region, game)

    suspend fun getViewsFromEntity(id: Long, game: Game?): List<String> =
        entitiesRepository.getViewsFromEntity(id, game)

    suspend fun delete(id: Long): Unit = entitiesRepository.delete(id)
}