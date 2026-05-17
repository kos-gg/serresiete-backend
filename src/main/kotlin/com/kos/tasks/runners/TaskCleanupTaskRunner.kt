package com.kos.tasks.runners

import com.kos.common.WithLogger
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository
import java.time.OffsetDateTime

class TaskCleanupTaskRunner(
    private val tasksRepository: TasksRepository
) : TaskRunner, WithLogger("taskCleanupTaskRunner") {

    override val type = TaskType.TASK_CLEANUP_TASK
    private val olderThanDays: Long = 7

    override suspend fun run(id: String, arguments: Map<String, String>?) {
        logger.info("Running task cleanup task")
        val deletedTasks = tasksRepository.deleteOldTasks(olderThanDays)
        logger.info("Deleted $deletedTasks old tasks")
        tasksRepository.insertTask(Task(id, type, TaskStatus(Status.SUCCESSFUL, "Deleted $deletedTasks old tasks"), OffsetDateTime.now()))
    }
}
