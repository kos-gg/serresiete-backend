package com.kos.tasks.runners

import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository
import java.time.OffsetDateTime

class UpdateWowHardcoreGuildsTaskRunner(
    private val tasksRepository: TasksRepository,
    private val entitiesService: EntitiesService
) : TaskRunner, WithLogger("updateWowHardcoreGuildsTaskRunner") {

    override val type = TaskType.UPDATE_WOW_HARDCORE_GUILDS

    override suspend fun run(id: String, arguments: Map<String, String>?) {
        logger.info("Updating wow hardcore guild entities")
        val errors = entitiesService.updateWowHardcoreGuilds()
        if (errors.isEmpty()) {
            tasksRepository.updateTask(Task(id, type, TaskStatus(Status.SUCCESSFUL, null), OffsetDateTime.now()))
        } else {
            tasksRepository.updateTask(
                Task(
                    id,
                    type,
                    TaskStatus(Status.ERROR, errors.joinToString(",\n") { it.toString() }),
                    OffsetDateTime.now()
                )
            )
        }
    }
}
