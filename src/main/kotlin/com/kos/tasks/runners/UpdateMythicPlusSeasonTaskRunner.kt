package com.kos.tasks.runners

import com.kos.common.WithLogger
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository
import java.time.OffsetDateTime

class UpdateMythicPlusSeasonTaskRunner(
    private val tasksRepository: TasksRepository,
    private val wowSeasonsService: WowSeasonService
) : TaskRunner, WithLogger("updateMythicPlusSeasonTaskRunner") {

    override val type = TaskType.UPDATE_MYTHIC_PLUS_SEASON

    override suspend fun run(id: String, arguments: Map<String, String>?) {
        logger.info("Running $type with id=$id")
        wowSeasonsService.addNewMythicPlusSeason()
            .onLeft {
                tasksRepository.updateTask(Task(id, type, TaskStatus(Status.ERROR, it.toString()), OffsetDateTime.now()))
            }
            .onRight {
                tasksRepository.updateTask(Task(id, type, TaskStatus(Status.SUCCESSFUL, "Updated Wow Season to season ${it.id} - ${it.name}"), OffsetDateTime.now()))
            }
    }
}
