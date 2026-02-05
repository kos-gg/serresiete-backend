package com.kos.sources

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.clients.domain.Season
import com.kos.common.error.ControllerError
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import com.kos.common.error.NotFound

class SourcesController(private val sourcesService: SourcesService) {
    suspend fun getWowStaticData(client: String?, activities: Set<Activity>): Either<ControllerError, Season> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.getWowStaticData)) {
                    when (val maybeData = sourcesService.getWowCurrentSeason()) {
                        null -> Either.Left(NotFound("wow static data current season"))
                        else -> Either.Right(maybeData)
                    }
                } else Either.Left(NotEnoughPermissions(client))
            }
        }
    }
}