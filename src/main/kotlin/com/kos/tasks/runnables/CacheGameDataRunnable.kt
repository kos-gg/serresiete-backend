package com.kos.tasks.runnables

import com.kos.common.WithLogger
import com.kos.datacache.DataCacheService
import com.kos.tasks.TaskType
import com.kos.tasks.TasksService
import com.kos.views.Game
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.util.*

data class CacheGameDataRunnable(
    val tasksService: TasksService,
    val dataCacheService: DataCacheService,
    val coroutineScope: CoroutineScope,
    val game: Game,
    val task: TaskType
) : Runnable, WithLogger("cacheGameDataTask") {

    override fun run() {
        coroutineScope.launch {
            logger.info("Running filling cache data task")
            tasksService.cacheDataTask(game, task, UUID.randomUUID().toString())
            val deletedRecords = dataCacheService.clearExpired(game, false)
            val deletionMessage = "Deleted $deletedRecords cached records"
            logger.info(deletionMessage)
        }
    }
}