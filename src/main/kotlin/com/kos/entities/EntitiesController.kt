package com.kos.entities

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.error.ControllerError
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import com.kos.datacache.DataCacheService
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