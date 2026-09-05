package com.kos.entities.repository

import com.kos.common.InMemoryRepository
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.domain.Entity
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest
import com.kos.views.Game
import com.kos.views.GetViewsQuery
import com.kos.views.repository.ViewsInMemoryRepository
import java.time.OffsetDateTime

class EntitiesInMemoryRepository(
    private val dataCacheRepository: DataCacheInMemoryRepository = DataCacheInMemoryRepository(),
    private val viewsRepository: ViewsInMemoryRepository = ViewsInMemoryRepository()
) :
    EntitiesRepository,
    InMemoryRepository {

    private val wowRepository = WowEntityInMemoryRepository(dataCacheRepository, ::nextId)
    private val wowHardcoreRepository = WowHardcoreEntityInMemoryRepository(dataCacheRepository, ::nextId)
    private val lolRepository = LolEntityInMemoryRepository(dataCacheRepository, ::nextId)

    private fun repositoryFor(game: Game): GameEntityRepository = when (game) {
        Game.WOW -> wowRepository
        Game.WOW_HC -> wowHardcoreRepository
        Game.LOL -> lolRepository
    }

    private suspend fun nextId(): Long {
        val allIds = wowRepository.state().map { it.id } +
                lolRepository.state().map { it.id } +
                wowHardcoreRepository.state().map { it.id }
        return if (allIds.isEmpty()) 1
        else allIds.maxBy { it } + 1
    }

    override suspend fun insert(
        entities: List<InsertEntityRequest>,
        game: Game
    ) = repositoryFor(game).insert(entities)

    override suspend fun update(
        id: Long,
        entity: InsertEntityRequest,
        game: Game
    ) = repositoryFor(game).update(id, entity)

    override suspend fun get(request: EntityRequest, game: Game): Entity? = repositoryFor(game).get(request)

    override suspend fun get(id: Long, game: Game): Entity? = repositoryFor(game).get(id)

    override suspend fun get(game: Game): List<Entity> = repositoryFor(game).getAll()

    override suspend fun get(entity: InsertEntityRequest, game: Game): Entity? = repositoryFor(game).get(entity)

    override suspend fun getEntitiesOlderThan(game: Game, olderThanMinutes: Long, maxEntities: Int): List<Entity> =
        repositoryFor(game).getOlderThan(olderThanMinutes, maxEntities)

    override suspend fun getViewsFromEntity(id: Long, game: Game?): List<String> {
        return viewsRepository.getViews(GetViewsQuery(game, false, null, null, includeMetadata = false))
            .second
            .filter { id in it.entitiesIds }
            .map { it.id }
    }

    override suspend fun delete(id: Long) {
        wowRepository.deleteIfPresent(id)
        lolRepository.deleteIfPresent(id)
        wowHardcoreRepository.deleteIfPresent(id)
    }

    override suspend fun state(): EntitiesState {
        return EntitiesState(wowRepository.state(), wowHardcoreRepository.state(), lolRepository.state())
    }

    override suspend fun withState(initialState: EntitiesState): EntitiesInMemoryRepository {
        wowRepository.withState(initialState.wowEntities)
        wowHardcoreRepository.withState(initialState.wowHardcoreEntities)
        lolRepository.withState(initialState.lolEntities)
        return this
    }

    override fun clear() {
        wowRepository.clear()
        wowHardcoreRepository.clear()
        lolRepository.clear()
        dataCacheRepository.clear()
    }
}

internal suspend fun <T : Entity> entitiesOlderThan(
    entities: List<T>,
    dataCacheRepository: DataCacheInMemoryRepository,
    threshold: OffsetDateTime,
    maxEntities: Int
): List<T> =
    entities
        .map { entity -> entity to dataCacheRepository.get(entity.id).maxByOrNull { it.inserted }?.inserted }
        .filter { (_, newestInserted) -> newestInserted == null || newestInserted.isBefore(threshold) }
        .sortedWith(compareBy(nullsFirst()) { (_, newestInserted) -> newestInserted })
        .take(maxEntities)
        .map { (entity, _) -> entity }
