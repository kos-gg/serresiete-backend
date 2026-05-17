package com.kos.tasks.runners

import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository
import com.kos.views.Game
import java.time.OffsetDateTime

class UpdateLolEntitiesTaskRunner(
    private val tasksRepository: TasksRepository,
    private val entitiesService: EntitiesService
) : TaskRunner, WithLogger("updateLolEntitiesTaskRunner") {

    override val type = TaskType.UPDATE_LOL_ENTITIES_TASK

    override suspend fun run(id: String, arguments: Map<String, String>?) {
        logger.info("Updating lol entities")
        val errors = entitiesService.updateEntities(Game.LOL)
        if (errors.isEmpty()) {
            tasksRepository.insertTask(Task(id, type, TaskStatus(Status.SUCCESSFUL, null), OffsetDateTime.now()))
        } else {
            tasksRepository.insertTask(Task(id, type, TaskStatus(Status.ERROR, errors.joinToString(",\n") { it.toString() }), OffsetDateTime.now()))
        }
    }
}
