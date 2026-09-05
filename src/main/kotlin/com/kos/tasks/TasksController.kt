package com.kos.tasks

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.error.ControllerError
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import com.kos.common.error.NotFound

class TasksController(private val tasksService: TasksService) {

    suspend fun runTask(
        client: String?,
        taskRequest: TaskRequest,
        activities: Set<Activity>
    ): Either<ControllerError, String> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.runTask)) {
                    Either.Right(tasksService.runTask(taskRequest.type, taskRequest.arguments))
                } else Either.Left(NotEnoughPermissions(client))
            }
        }
    }

    suspend fun getTasks(
        client: String?,
        activities: Set<Activity>,
        taskType: TaskType?
    ): Either<ControllerError, List<Task>> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                if (activities.contains(Activities.getTasks)) Either.Right(tasksService.getTasks(taskType))
                else Either.Left(NotEnoughPermissions(client))
            }
        }
    }

    suspend fun getTask(client: String?, id: String, activities: Set<Activity>): Either<ControllerError, Task> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> {
                when (val maybeTask = tasksService.getTask(id)) {
                    null -> Either.Left(NotFound(id))
                    else -> {
                        if (activities.contains(Activities.getTask)) Either.Right(maybeTask)
                        else Either.Left(NotEnoughPermissions(client))
                    }
                }
            }
        }
    }
}