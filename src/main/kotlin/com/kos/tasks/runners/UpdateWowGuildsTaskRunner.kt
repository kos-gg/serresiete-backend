package com.kos.tasks.runners

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.tasks.TaskCompleted
import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskType

class UpdateWowGuildsTaskRunner(
    private val entitiesService: EntitiesService
) : TaskRunner, WithLogger("updateWowGuildsTaskRunner") {

    override val type = TaskType.UPDATE_WOW_GUILDS

    override suspend fun run(arguments: Map<String, String>?): Either<TaskFailed, TaskCompleted> = either {
        logger.info("Updating wow guild entities")
        val errors = entitiesService.updateWowGuilds()
        ensure(errors.isEmpty()) { TaskFailed(errors.joinToString(",\n") { it.error() }) }
        TaskCompleted()
    }
}
