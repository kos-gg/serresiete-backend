package com.kos.entities

import arrow.core.Either
import com.kos.common.error.ServiceError
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.ResolvedEntities

interface EntityResolver {
    suspend fun resolve(
        requested: List<EntityRequest>
    ): Either<ServiceError, ResolvedEntities>
}
