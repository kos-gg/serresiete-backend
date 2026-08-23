package com.kos.tasks.runners

import com.kos.common.WithLogger
import com.kos.datacache.DataCacheService
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository
import com.kos.views.Game
import java.time.OffsetDateTime

class CacheClearTaskRunner(
    private val tasksRepository: TasksRepository,
    private val dataCacheService: DataCacheService
) : TaskRunner, WithLogger("cacheClearTaskRunner") {

    override val type = TaskType.CACHE_CLEAR_TASK

    override suspend fun run(id: String, arguments: Map<String, String>?) {
        val game = arguments?.get("game")
            ?.let { Game.fromString(it) }
            ?.onLeft { logger.warn(it.toString()) }
            ?.getOrNull()
        logger.info("Running cache cleanup task")
        val deletedRecords = dataCacheService.clearCache(game)
        logger.info("Deleted $deletedRecords records")
        tasksRepository.updateTask(
            Task(
                id,
                type,
                TaskStatus(Status.SUCCESSFUL, "Deleted $deletedRecords old tasks"),
                OffsetDateTime.now()
            )
        )
    }
}
