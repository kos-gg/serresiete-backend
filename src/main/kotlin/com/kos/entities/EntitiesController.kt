package com.kos.entities

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.*
import com.kos.datacache.DataCacheService
import com.kos.entities.repository.EntitiesRepository
import com.kos.views.Game

class EntitiesController(val dataCacheService: DataCacheService, val entitiesRepository: EntitiesRepository) {
    suspend fun getEntityData(
        client: String?,
        searchRequestAndGame: Pair<CreateEntityRequest, Game>?,
        activities: Set<Activity>
    ): Either<ControllerError, EntityDataResponse> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.searchEntity)) {
                    searchRequestAndGame._fold(
                        left = { Either.Left(BadRequest("problemmmm")) },
                        right = {
                            entitiesRepository.get(it.first, it.second)
                        }
                    )

                } else Either.Left(NotEnoughPermissions(client))
            }
        }
    }

}