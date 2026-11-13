package com.kos.tasks.runners

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.tasks.TaskCompleted
import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskType

class UpdateWowForeverGuildsTaskRunner(
    private val entitiesService: EntitiesService
) : TaskRunner, WithLogger("updateWowForeverGuildsTaskRunner") {

    override val type = TaskType.UPDATE_WOW_FOREVER_GUILDS

    override suspend fun run(arguments: Map<String, String>?): Either<TaskFailed, TaskCompleted> = either {
        logger.info("Updating wow forever guild entities")
        val errors = entitiesService.updateWowForeverGuilds()
        ensure(errors.isEmpty()) { TaskFailed(errors.joinToString(",\n") { it.error() }) }
        TaskCompleted()
    }
}
