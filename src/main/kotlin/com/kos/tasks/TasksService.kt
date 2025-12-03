package com.kos.tasks

import com.kos.auth.AuthService
import com.kos.common.WithLogger
import com.kos.common.WowHardcoreCharacterIsDead
import com.kos.datacache.DataCacheService
import com.kos.entities.EntitiesService
import com.kos.entities.LolEntity
import com.kos.entities.cache.EntityCacheServiceRegistry
import com.kos.tasks.repository.TasksRepository
import com.kos.views.Game
import java.time.OffsetDateTime

data class TasksService(
    private val tasksRepository: TasksRepository,
    private val dataCacheService: DataCacheService,
    private val entitiesService: EntitiesService,
    private val authService: AuthService,
    private val entityCacheServiceRegistry: EntityCacheServiceRegistry
) : WithLogger("tasksService") {

    private val olderThanDays: Long = 7

    suspend fun getTasks(taskType: TaskType?) = tasksRepository.getTasks(taskType)

    suspend fun getTask(id: String) = tasksRepository.getTask(id)

    suspend fun runTask(taskType: TaskType, taskId: String, arguments: Map<String, String>?) {
        when (taskType) {
            TaskType.TOKEN_CLEANUP_TASK -> tokenCleanup(taskId)
            TaskType.CACHE_LOL_DATA_TASK -> cacheDataTask(Game.LOL, taskType, taskId)
            TaskType.CACHE_WOW_DATA_TASK -> cacheDataTask(Game.WOW, taskType, taskId)
            TaskType.CACHE_WOW_HC_DATA_TASK -> cacheDataTask(Game.WOW_HC, taskType, taskId)
            TaskType.TASK_CLEANUP_TASK -> taskCleanup(taskId)
            TaskType.UPDATE_LOL_ENTITIES_TASK -> updateLolEntities(taskId)
            TaskType.CACHE_CLEAR_TASK -> {
                val game = arguments?.get("game")?.let { Game.fromString(it) }
                    ?.onLeft { logger.warn(it.toString()) }
                    ?.getOrNull()
                cacheCleanup(game, taskType, taskId)
            }
            TaskType.UPDATE_WOW_HARDCORE_GUILDS -> updateWowGuildEntities(taskId)
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun updateLolEntities(id: String) {
        logger.info("Updating lol entities")
        val errors = entitiesService.updateEntities(Game.LOL)
        if (errors.isEmpty()) {
            tasksRepository.insertTask(
                Task(
                    id,
                    TaskType.UPDATE_LOL_ENTITIES_TASK,
                    TaskStatus(Status.SUCCESSFUL, null),
                    OffsetDateTime.now()
                )
            )
        } else {
            tasksRepository.insertTask(
                Task(
                    id,
                    TaskType.UPDATE_LOL_ENTITIES_TASK,
                    TaskStatus(Status.ERROR, errors.joinToString(",\n") { it.toString() }),
                    OffsetDateTime.now()
                )
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    suspend fun updateWowGuildEntities(id: String) {
        logger.info("Updating wow hardcore guild entities")
        val errors = entitiesService.updateWowHardcoreGuilds()
        if (errors.isEmpty()) {
            tasksRepository.insertTask(
                Task(
                    id,
                    TaskType.UPDATE_WOW_HARDCORE_GUILDS,
                    TaskStatus(Status.SUCCESSFUL, null),
                    OffsetDateTime.now()
                )
            )
        } else {
            tasksRepository.insertTask(
                Task(
                    id,
                    TaskType.UPDATE_WOW_HARDCORE_GUILDS,
                    TaskStatus(Status.ERROR, errors.joinToString(",\n") { it.toString() }),
                    OffsetDateTime.now()
                )
            )
        }
    }

    suspend fun taskCleanup(id: String) {
        logger.info("Running task cleanup task")
        val deletedTasks = tasksRepository.deleteOldTasks(olderThanDays)
        logger.info("Deleted $deletedTasks old tasks")
        tasksRepository.insertTask(
            Task(
                id,
                TaskType.TASK_CLEANUP_TASK,
                TaskStatus(Status.SUCCESSFUL, "Deleted $deletedTasks old tasks"),
                OffsetDateTime.now()
            )
        )
    }

    private suspend fun cacheCleanup(game: Game?, taskType: TaskType, id: String) {
        logger.info("Running cache cleanup task")
        val deletedRecords = dataCacheService.clearCache(game)
        logger.info("Deleted $deletedRecords old tasks")
        tasksRepository.insertTask(
            Task(
                id,
                taskType,
                TaskStatus(Status.SUCCESSFUL, "Deleted $deletedRecords old tasks"),
                OffsetDateTime.now()
            )
        )
    }

    suspend fun tokenCleanup(id: String) {
        logger.info("Running token cleanup task")
        val deletedTokens = authService.deleteExpiredTokens()
        logger.info("Deleted $deletedTokens expired tokens")
        tasksRepository.insertTask(
            Task(
                id,
                TaskType.TOKEN_CLEANUP_TASK,
                TaskStatus(Status.SUCCESSFUL, "Deleted $deletedTokens expired tokens"),
                OffsetDateTime.now()
            )
        )
    }

    suspend fun cacheDataTask(game: Game, taskType: TaskType, id: String) {
        logger.info("Running $taskType")
        val entities = entitiesService.getEntitiesToSync(game, 30)
        logger.debug("entities to be synced: {}", entities.map { it.id }.joinToString(","))

        val errors = entityCacheServiceRegistry.serviceFor(game).cache(entities)

        //TODO: improve the check of excluded error from being flagged as error
        if (errors.isEmpty() || errors.all { it is WowHardcoreCharacterIsDead }) {
            tasksRepository.insertTask(
                Task(
                    id,
                    taskType,
                    TaskStatus(Status.SUCCESSFUL, "entities synced: ${entities.map { it.id }.joinToString { "," }}"),
                    OffsetDateTime.now()
                )
            )
        } else {
            //TODO: depending on the error, decide what to do with the task (not a true error, etc)
            tasksRepository.insertTask(
                Task(
                    id,
                    taskType,
                    TaskStatus(Status.ERROR, errors.joinToString(",\n") { it.error() }),
                    OffsetDateTime.now()
                )
            )
        }
    }
}