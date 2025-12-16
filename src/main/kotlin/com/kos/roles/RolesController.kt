package com.kos.roles

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.error.ControllerError
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions

class RolesController(private val rolesService: RolesService) {
    suspend fun getRoles(client: String?, activities: Set<Activity>): Either<ControllerError, List<Role>> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.getAnyRoles)) Either.Right(rolesService.getRoles())
                else Either.Left(NotEnoughPermissions(client))
            }
        }
    }

    suspend fun getRole(
        client: String?,
        activities: Set<Activity>,
        role: Role
    ): Either<ControllerError, Pair<Role, Set<Activity>>> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if(activities.contains(Activities.getAnyRoles)) Either.Right(rolesService.getRole(role))
                else Either.Left(NotEnoughPermissions(client))
            }
        }
    }

    suspend fun setActivities(
        client: String?,
        activities: Set<Activity>,
        role: Role,
        request: ActivitiesRequest
    ): Either<ControllerError, Unit> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if(activities.contains(Activities.addActivityToRole)) Either.Right(rolesService.setActivitiesToRole(role, request.activities))
                else Either.Left(NotEnoughPermissions(client))
            }
        }
    }
}