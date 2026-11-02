package com.kos.tasks

import arrow.core.Either
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

        tasksRepository.insertTask(Task(taskId, taskType, TaskStatus(Status.PENDING), OffsetDateTime.now()))

        scope.launch {
            val task = Either.catch { taskRunner.run(arguments) }.fold(
                {
                    logger.error("Task $taskType $taskId failed", it)
                    failed(taskId, taskType, arguments, TaskFailed(it.stackTraceToString()))
                },
                { taskResult ->
                    taskResult.fold(
                        { failed(taskId, taskType, arguments, it) },
                        { completed(taskId, taskType, it) }
                    )
                }
            )
            tasksRepository.updateTask(task)
        }

        return taskId
    }

    private fun failed(id: String, type: TaskType, arguments: Map<String, String>?, failure: TaskFailed) =
        Task(
            id,
            type,
            TaskStatus(Status.ERROR, failure.retryAfter),
            OffsetDateTime.now(),
            TaskError(arguments, failure.detail)
        )

    private fun completed(id: String, type: TaskType, completion: TaskCompleted) =
        Task(id, type, TaskStatus(Status.SUCCESSFUL, completion.retryAfter), OffsetDateTime.now())
}