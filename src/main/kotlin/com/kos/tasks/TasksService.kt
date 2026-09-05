package com.kos.tasks

import com.kos.common.WithLogger
import com.kos.tasks.repository.TasksRepository
import com.kos.tasks.runners.TaskRunnerProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.OffsetDateTime
import java.util.*

data class TasksService(
    private val tasksRepository: TasksRepository,
    private val taskRunnerProvider: TaskRunnerProvider
) : WithLogger("tasksService") {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    suspend fun getTasks(taskType: TaskType?) = tasksRepository.getTasks(taskType)

    suspend fun getTask(id: String) = tasksRepository.getTask(id)

    suspend fun runTask(taskType: TaskType, arguments: Map<String, String>?): String {
        val taskRunner = checkNotNull(taskRunnerProvider.taskRunnerFor(taskType)) {
            "No runner found for task type $taskType"
        }
        val taskId = UUID.randomUUID().toString()

        tasksRepository.insertTask(Task(taskId, taskType, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        scope.launch {
            try {
                taskRunner.run(taskId, arguments)
            } catch (e: Exception) {
                tasksRepository.updateTask(
                    Task(
                        taskId,
                        taskType,
                        TaskStatus(Status.ERROR, e.message ?: e.stackTraceToString()),
                        OffsetDateTime.now()
                    )
                )
            }
        }

        return taskId
    }
}