package com.kos.tasks.runners

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.tasks.TaskCompleted
import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskType
import com.kos.views.Game

class UpdateLolEntitiesTaskRunner(
    private val entitiesService: EntitiesService
) : TaskRunner, WithLogger("updateLolEntitiesTaskRunner") {

    override val type = TaskType.UPDATE_LOL_ENTITIES_TASK

    override suspend fun run(arguments: Map<String, String>?): Either<TaskFailed, TaskCompleted> = either {
        logger.info("Updating lol entities")
        val errors = entitiesService.updateEntities(Game.LOL)
        ensure(errors.isEmpty()) { TaskFailed(errors.joinToString(",\n") { it.error() }) }
        TaskCompleted()
    }
}
