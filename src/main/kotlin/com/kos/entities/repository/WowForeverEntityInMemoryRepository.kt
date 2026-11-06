package com.kos.entities.repository

import com.kos.common.InMemoryRepository
import com.kos.common.WithState
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.domain.*
import java.time.OffsetDateTime

class WowForeverEntityInMemoryRepository(
    private val dataCacheRepository: DataCacheInMemoryRepository,
    private val nextId: suspend () -> Long
) :
    GameEntityRepository,
    WithState<List<WowForeverEntity>, WowForeverEntityInMemoryRepository>,
    InMemoryRepository {

    private val entities: MutableList<WowForeverEntity> = mutableListOf()

    private fun WowForeverEntityRequest.normalized() = copy(name = name.lowercase(), surname = surname.lowercase())
    private fun WowForeverEntity.normalized() = copy(name = name.lowercase(), surname = surname.lowercase())

    override suspend fun insert(entities: List<InsertEntityRequest>): List<Entity> {
        val requests = entities.map {
            check(it is WowForeverEntityRequest) { "Error inserting entity $it" }
            it.normalized()
        }
        check(requests.none { request -> this.entities.any { request.same(it) } }) { "Error inserting entities $requests" }
        val inserted = requests.map { it.toEntity(nextId()) }
        this.entities.addAll(inserted)
        return inserted
    }

    override suspend fun update(id: Long, entity: InsertEntityRequest): Int =
        when (entity) {
            is WowForeverEntityRequest -> {
                val index = entities.indexOfFirst { it.id == id }
                val current = entities[index]
                val normalized = entity.normalized()
                entities[index] = current.copy(
                    name = normalized.name,
                    surname = normalized.surname,
                    region = normalized.region,
                    blizzardId = normalized.blizzardId ?: current.blizzardId
                )
                1
            }

            else -> throw IllegalArgumentException("error updating $id $entity for WOW_FOREVER")
        }

    override suspend fun get(id: Long): Entity? = entities.find { it.id == id }

    override suspend fun get(request: EntityRequest): Entity? {
        val normalized = (request as WowForeverEntityRequest).normalized()
        return entities.find {
            it.name == normalized.name && it.surname == normalized.surname && it.region == normalized.region
        }
    }

    override suspend fun get(entity: InsertEntityRequest): Entity? {
        val normalized = (entity as WowForeverEntityRequest).normalized()
        return entities.find { normalized.same(it) }
    }

    override suspend fun getAll(): List<Entity> = entities

    override suspend fun getOlderThan(olderThanMinutes: Long, maxEntities: Int): List<Entity> {
        val threshold = OffsetDateTime.now().minusMinutes(olderThanMinutes)
        return entitiesOlderThan(entities, dataCacheRepository, threshold, maxEntities)
    }

    fun deleteIfPresent(id: Long): Boolean = entities.removeIf { it.id == id }

    override fun clear() = entities.clear()

    override suspend fun state(): List<WowForeverEntity> = entities.toList()

    override suspend fun withState(initialState: List<WowForeverEntity>): WowForeverEntityInMemoryRepository {
        entities.addAll(initialState.map { it.normalized() })
        return this
    }
}
