package com.kos.tasks

import com.kos.common.WithLogger
import com.kos.tasks.repository.TasksRepository
import com.kos.tasks.runners.TaskRunnerProvider

data class TasksService(
    private val tasksRepository: TasksRepository,
    private val taskRunnerProvider: TaskRunnerProvider
) : WithLogger("tasksService") {

    suspend fun getTasks(taskType: TaskType?) = tasksRepository.getTasks(taskType)

    suspend fun getTask(id: String) = tasksRepository.getTask(id)

    suspend fun runTask(taskType: TaskType, taskId: String, arguments: Map<String, String>?) {
        taskRunnerProvider.runnerFor(taskType)?.run(taskId, arguments)
            ?: logger.error("No runner found for task type $taskType")
    }
}