package com.kos.tasks.runners

import com.kos.auth.AuthService
import com.kos.common.WithLogger
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository
import java.time.OffsetDateTime

class TokenCleanupTaskRunner(
    private val tasksRepository: TasksRepository,
    private val authService: AuthService
) : TaskRunner, WithLogger("tokenCleanupTaskRunner") {

    override val type = TaskType.TOKEN_CLEANUP_TASK

    override suspend fun run(id: String, arguments: Map<String, String>?) {
        logger.info("Running token cleanup task")
        val deletedTokens = authService.deleteExpiredTokens()
        logger.info("Deleted $deletedTokens expired tokens")
        tasksRepository.insertTask(Task(id, type, TaskStatus(Status.SUCCESSFUL, "Deleted $deletedTokens expired tokens"), OffsetDateTime.now()))
    }
}
