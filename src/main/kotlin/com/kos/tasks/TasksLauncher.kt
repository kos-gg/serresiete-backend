package com.kos.tasks

import com.kos.auth.AuthService
import com.kos.common.WithLogger
import com.kos.datacache.DataCacheService
import com.kos.tasks.repository.TasksRepository
import com.kos.tasks.runnables.CacheGameDataRunnable
import com.kos.tasks.runnables.TasksCleanupRunnable
import com.kos.tasks.runnables.TokenCleanupRunnable
import com.kos.tasks.runnables.UpdateLolEntitiesRunnable
import com.kos.views.Game
import kotlinx.coroutines.CoroutineScope
import java.time.Duration
import java.time.OffsetDateTime
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.TimeUnit

data class TasksLauncher(
    private val tasksService: TasksService,
    private val tasksRepository: TasksRepository,
    private val executorService: ScheduledExecutorService,
    private val authService: AuthService,
    private val dataCacheService: DataCacheService,
    private val coroutineScope: CoroutineScope
) : WithLogger("tasksLauncher") {
    suspend fun launchTasks() {
        val now = OffsetDateTime.now()
        val wowDataTaskDelay = 30
        val lolDataTaskDelay = 30
        val tokenCleanupDelay = 15
        val tasksCleanupDelay = 10080
        val updateLolEntitiesDelay = 1440


        suspend fun getTaskInitialDelay(now: OffsetDateTime, taskType: TaskType, timeDelay: Int): Long =
            tasksRepository.getLastExecution(taskType)?.inserted?.let {
                val differenceBetweenNowAndLastExecutionTime = Duration.between(it, now).toMinutes()
                return if (differenceBetweenNowAndLastExecutionTime >= timeDelay) 0
                else timeDelay - differenceBetweenNowAndLastExecutionTime
            } ?: 0

        val cacheWowDataTaskInitDelay: Long = getTaskInitialDelay(now, TaskType.CACHE_WOW_DATA_TASK, wowDataTaskDelay)
        val cacheWowHcDataTaskInitDelay: Long = getTaskInitialDelay(now, TaskType.CACHE_WOW_HC_DATA_TASK, wowDataTaskDelay)
        val cacheLolDataTaskInitDelay: Long = getTaskInitialDelay(now, TaskType.CACHE_LOL_DATA_TASK, lolDataTaskDelay)
        val tokenCleanupInitDelay: Long = getTaskInitialDelay(now, TaskType.TOKEN_CLEANUP_TASK, tokenCleanupDelay)
        val tasksCleanupInitDelay: Long = getTaskInitialDelay(now, TaskType.TASK_CLEANUP_TASK, tasksCleanupDelay)
        val updateLolEntitiesInitDelay: Long = getTaskInitialDelay(now, TaskType.UPDATE_LOL_ENTITIES_TASK, tasksCleanupDelay)

        logger.info("Setting $cacheWowDataTaskInitDelay minutes of delay before launching ${TaskType.CACHE_WOW_DATA_TASK}")
        logger.info("Setting $cacheLolDataTaskInitDelay minutes of delay before launching ${TaskType.CACHE_LOL_DATA_TASK}")
        logger.info("Setting $cacheWowHcDataTaskInitDelay minutes of delay before launching ${TaskType.CACHE_WOW_HC_DATA_TASK}")
        logger.info("Setting $tokenCleanupInitDelay minutes of delay before launching ${TaskType.TOKEN_CLEANUP_TASK}")
        logger.info("Setting $tasksCleanupInitDelay minutes of delay before launching ${TaskType.TASK_CLEANUP_TASK}")
        logger.info("Setting $updateLolEntitiesInitDelay minutes of delay before launching ${TaskType.UPDATE_LOL_ENTITIES_TASK}")


        executorService.scheduleAtFixedRate(
            TokenCleanupRunnable(tasksService, coroutineScope),
            tokenCleanupInitDelay, tokenCleanupDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            CacheGameDataRunnable(
                tasksService,
                dataCacheService,
                coroutineScope,
                Game.LOL,
                TaskType.CACHE_LOL_DATA_TASK
            ),
            cacheLolDataTaskInitDelay, lolDataTaskDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            CacheGameDataRunnable(
                tasksService,
                dataCacheService,
                coroutineScope,
                Game.WOW,
                TaskType.CACHE_WOW_DATA_TASK
            ),
            cacheWowDataTaskInitDelay, wowDataTaskDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            CacheGameDataRunnable(
                tasksService,
                dataCacheService,
                coroutineScope,
                Game.WOW_HC,
                TaskType.CACHE_WOW_HC_DATA_TASK
            ),
            cacheWowHcDataTaskInitDelay, wowDataTaskDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            TasksCleanupRunnable(
                tasksService,
                coroutineScope
            ),
            tasksCleanupInitDelay, tasksCleanupDelay.toLong(), TimeUnit.MINUTES
        )

        executorService.scheduleAtFixedRate(
            UpdateLolEntitiesRunnable(
                tasksService,
                coroutineScope
            ),
            updateLolEntitiesInitDelay, updateLolEntitiesDelay.toLong(), TimeUnit.MINUTES
        )

        Runtime.getRuntime().addShutdownHook(Thread {
            executorService.shutdown()
        })
    }
}