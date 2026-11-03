package com.kos.tasks.runners

import arrow.core.Either
import arrow.core.raise.either
import com.kos.auth.AuthService
import com.kos.common.WithLogger
import com.kos.tasks.TaskCompleted
import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskType

class TokenCleanupTaskRunner(
    private val authService: AuthService
) : TaskRunner, WithLogger("tokenCleanupTaskRunner") {

    override val type = TaskType.TOKEN_CLEANUP_TASK

    override suspend fun run(arguments: Map<String, String>?): Either<TaskFailed, TaskCompleted> = either {
        logger.info("Running token cleanup task")
        val deletedTokens = authService.deleteExpiredTokens()
        logger.info("Deleted $deletedTokens expired tokens")
        TaskCompleted()
    }
}
