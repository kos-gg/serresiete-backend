package com.kos.entities.repository

import arrow.core.Either
import com.kos.common.error.RepositoryError
import com.kos.entities.domain.Entity
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.InsertEntityRequest

interface GameEntityRepository {
    suspend fun insert(entities: List<InsertEntityRequest>): Either<RepositoryError, List<Entity>>
    suspend fun update(id: Long, entity: InsertEntityRequest): Either<RepositoryError, Int>
    suspend fun get(id: Long): Entity?
    suspend fun get(request: EntityRequest): Entity?
    suspend fun get(entity: InsertEntityRequest): Entity?
    suspend fun getAll(): List<Entity>
    suspend fun getOlderThan(olderThanMinutes: Long, maxEntities: Int): List<Entity>
}
