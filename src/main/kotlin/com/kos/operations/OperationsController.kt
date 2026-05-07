package com.kos.operations

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.error.ControllerError
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import com.kos.common.error.NotFound

class OperationsController(private val operationsService: OperationsService) {

    suspend fun getOperationStatus(
        client: String?,
        operationId: String,
        activities: Set<Activity>
    ): Either<ControllerError, OperationStatus> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (!activities.contains(Activities.getOperationStatus))
                    return Either.Left(NotEnoughPermissions(client))
                when (val status = operationsService.getOperationStatus(operationId)) {
                    null -> Either.Left(NotFound(operationId))
                    else -> Either.Right(status)
                }
            }
        }
    }
}
