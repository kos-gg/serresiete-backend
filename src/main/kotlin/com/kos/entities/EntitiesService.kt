package com.kos.entities

import arrow.core.Either
import com.kos.common.WithLogger
import com.kos.common.error.InsertError
import com.kos.common.error.ResolverNotFound
import com.kos.common.error.ServiceError
import com.kos.common.fold
import com.kos.entities.domain.*
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.wowguilds.WowGuildsRepository
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.Game
import com.kos.views.ViewExtraArguments

data class EntitiesService(
    private val entitiesRepository: EntitiesRepository,
    private val wowGuildsRepository: WowGuildsRepository,
    private val entitiesResolverProvider: EntityResolverProvider,
    private val lolUpdater: LolEntityUpdater,
    private val wowHardcoreGuildUpdater: WowHardcoreGuildUpdater
) : WithLogger("EntitiesService") {

    suspend fun resolveEntities(
        requestedEntities: List<CreateEntityRequest>,
        game: Game,
        extraArguments: ViewExtraArguments? = null
    ): Either<ServiceError, ResolvedEntities> {
        return entitiesResolverProvider
            .resolverFor(game)
            .fold(
                left = { Either.Left(ResolverNotFound(game)) },
                right = { it.resolve(requestedEntities, extraArguments) }
            )
    }

    suspend fun updateEntities(
        game: Game
    ): List<ServiceError> {
        @Suppress("UNCHECKED_CAST")
        return when (game) {
            Game.LOL -> lolUpdater.update(entitiesRepository.get(game) as List<LolEntity>)
            Game.WOW -> listOf()
            Game.WOW_HC -> listOf()
        }
    }

    suspend fun updateWowHardcoreGuilds(): List<ServiceError> {
        val guildsWithViewId = wowGuildsRepository.getGuilds()
        return wowHardcoreGuildUpdater.update(guildsWithViewId)
    }

    suspend fun get(id: Long, game: Game): Entity? = entitiesRepository.get(id, game)
    suspend fun get(game: Game): List<Entity> = entitiesRepository.get(game)
    suspend fun getEntitiesToSync(game: Game, olderThanMinutes: Long) =
        entitiesRepository.getEntitiesToSync(game, olderThanMinutes)

    suspend fun insert(entities: List<InsertEntityRequest>, game: Game) = entitiesRepository.insert(entities, game)
    suspend fun insertGuild(payload: GuildPayload, viewId: String): Either<InsertError, Unit> =
        wowGuildsRepository.insertGuild(payload.blizzardId, payload.name, payload.realm, payload.region, viewId)

    suspend fun getViewsFromEntity(id: Long, game: Game?): List<String> =
        entitiesRepository.getViewsFromEntity(id, game)

    suspend fun delete(id: Long): Unit = entitiesRepository.delete(id)
}