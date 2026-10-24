package com.kos.entities

import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.ResolvedEntities

interface EntityResolver {
    suspend fun resolve(requested: List<EntityRequest>): ResolvedEntities
}
