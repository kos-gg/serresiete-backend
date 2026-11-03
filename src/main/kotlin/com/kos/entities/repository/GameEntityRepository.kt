package com.kos.entities.repository

import com.kos.entities.domain.Entity
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest

interface GameEntityRepository {
    suspend fun insert(entities: List<InsertEntityRequest>): List<Entity>
    suspend fun update(id: Long, entity: InsertEntityRequest): Int
    suspend fun get(id: Long): Entity?
    suspend fun get(request: EntityRequest): Entity?
    suspend fun get(entity: InsertEntityRequest): Entity?
    suspend fun getAll(): List<Entity>
    suspend fun getOlderThan(olderThanMinutes: Long, maxEntities: Int): List<Entity>
}
