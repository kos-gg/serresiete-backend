package com.kos.entities

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.ControllerError
import com.kos.common.NotAuthorized
import com.kos.common.NotEnoughPermissions
import com.kos.datacache.DataCacheService
import com.kos.entities.domain.CreateEntityRequest
import com.kos.entities.domain.EntityDataResponse
import com.kos.views.Game

class EntitiesController(
    private val dataCacheService: DataCacheService
) {
    suspend fun getEntityData(
        client: String?,
        activities: Set<Activity>,
        maybeSearchRequestAndGame: Pair<CreateEntityRequest, Game>
    ): Either<ControllerError, EntityDataResponse> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.searchEntity)) dataCacheService.getOrSync(maybeSearchRequestAndGame)
                else Either.Left(NotEnoughPermissions(client))
            }
        }
    }
}