package com.kos.entities

import arrow.core.Either
import com.kos.common.error.ServiceError
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.ResolvedEntities
import com.kos.views.Game

interface EntityResolver {
    val game: Game

    suspend fun resolve(
        requested: List<EntityRequest>
    ): Either<ServiceError, ResolvedEntities>
}
