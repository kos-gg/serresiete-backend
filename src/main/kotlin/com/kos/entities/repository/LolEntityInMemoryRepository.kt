package com.kos.entities.repository

import com.kos.common.InMemoryRepository
import com.kos.common.WithState
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.domain.*
import java.time.OffsetDateTime

class LolEntityInMemoryRepository(
    private val dataCacheRepository: DataCacheInMemoryRepository,
    private val nextId: suspend () -> Long
) :
    GameEntityRepository,
    WithState<List<LolEntity>, LolEntityInMemoryRepository>,
    InMemoryRepository {

    private val entities: MutableList<LolEntity> = mutableListOf()

    override suspend fun insert(entities: List<InsertEntityRequest>): List<Entity> {
        val initial = this.entities.toList()
        val inserted = entities.fold(listOf<Entity>()) { acc, it ->
            when (it) {
                is LolEnrichedEntityRequest -> {
                    if (this.entities.any { entity -> it.same(entity) }) {
                        this.entities.clear()
                        this.entities.addAll(initial)
                        throw IllegalStateException("Error inserting chracter $it")
                    }
                    val entity = it.toEntity(nextId())
                    this.entities.add(entity)
                    acc + entity
                }

                else -> {
                    this.entities.clear()
                    this.entities.addAll(initial)
                    throw IllegalStateException("Error inserting chracter $it")
                }
            }
        }
        return inserted
    }

    override suspend fun update(id: Long, entity: InsertEntityRequest): Int =
        when (entity) {
            is LolEnrichedEntityRequest -> {
                val index = entities.indexOfFirst { it.id == id }
                entities.removeAt(index)
                entities.add(
                    index,
                    LolEntity(id, entity.name, entity.tag, entity.puuid, entity.summonerIconId, entity.summonerLevel)
                )
                1
            }

            else -> throw IllegalArgumentException("error updating $id $entity for LOL")
        }

    override suspend fun get(id: Long): Entity? = entities.find { it.id == id }

    override suspend fun get(request: EntityRequest): Entity? {
        request as LolEntityRequest
        return entities.find {
            it.name == request.name &&
                    it.tag == request.tag
        }
    }

    override suspend fun get(entity: InsertEntityRequest): Entity? = entities.find { entity.same(it) }

    override suspend fun getAll(): List<Entity> = entities

    override suspend fun getOlderThan(olderThanMinutes: Long, maxEntities: Int): List<Entity> {
        val threshold = OffsetDateTime.now().minusMinutes(olderThanMinutes)
        return entitiesOlderThan(entities, dataCacheRepository, threshold, maxEntities)
    }

    fun deleteIfPresent(id: Long): Boolean {
        val index = entities.indexOfFirst { it.id == id }
        if (index == -1) return false
        entities.removeAt(index)
        return true
    }

    override fun clear() = entities.clear()

    override suspend fun state(): List<LolEntity> = entities.toList()

    override suspend fun withState(initialState: List<LolEntity>): LolEntityInMemoryRepository {
        entities.addAll(initialState)
        return this
    }
}
