package com.kos.tasks.runners

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.WithLogger
import com.kos.tasks.TaskCompleted
import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository

class TaskCleanupTaskRunner(
    private val tasksRepository: TasksRepository
) : TaskRunner, WithLogger("taskCleanupTaskRunner") {

    override val type = TaskType.TASK_CLEANUP_TASK
    private val olderThanDays: Long = 7

    override suspend fun run(arguments: Map<String, String>?): Either<TaskFailed, TaskCompleted> = either {
        logger.info("Running task cleanup task")
        val deletedTasks = tasksRepository.deleteOldTasks(olderThanDays)
        logger.info("Deleted $deletedTasks old tasks")
        TaskCompleted()
    }
}
