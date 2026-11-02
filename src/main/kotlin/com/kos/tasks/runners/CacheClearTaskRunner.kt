package com.kos.tasks.runners

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.WithLogger
import com.kos.datacache.DataCacheService
import com.kos.tasks.TaskCompleted
import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskType
import com.kos.views.Game

class CacheClearTaskRunner(
    private val dataCacheService: DataCacheService
) : TaskRunner, WithLogger("cacheClearTaskRunner") {

    override val type = TaskType.CACHE_CLEAR_TASK

    override suspend fun run(arguments: Map<String, String>?): Either<TaskFailed, TaskCompleted> = either {
        val game = arguments?.get("game")
            ?.let { Game.fromString(it) }
            ?.onLeft { logger.warn(it.toString()) }
            ?.getOrNull()
        logger.info("Running cache cleanup task")
        val deletedRecords = dataCacheService.clearCache(game)
        logger.info("Deleted $deletedRecords records")
        TaskCompleted()
    }
}
