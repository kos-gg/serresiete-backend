package com.kos.tasks.runners

import com.kos.common.WithLogger
import com.kos.common.error.SynchronizerNotFound
import com.kos.common.fold
import com.kos.datacache.EntitySynchronizerProvider
import com.kos.entities.EntitiesService
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository
import com.kos.views.Game
import java.time.OffsetDateTime

abstract class CacheGameDataTaskRunner(
    protected val game: Game,
    private val tasksRepository: TasksRepository,
    private val entitiesService: EntitiesService,
    private val entitySynchronizerProvider: EntitySynchronizerProvider
) : TaskRunner, WithLogger("cacheGameDataTaskRunner") {

    override suspend fun run(id: String, arguments: Map<String, String>?) {
        logger.info("Running $type")
        val entities = entitiesService.getEntitiesToSync(game, 30)
        logger.debug("entities to be synced: {}", entities.map { it.id }.joinToString(","))
        val synchronizer = entitySynchronizerProvider.synchronizerFor(game)
        val errors = synchronizer.fold(
            left = { listOf(SynchronizerNotFound(game)) },
            right = {
                it.synchronize(entities)
                    .filter { syncErrors -> synchronizer?.isSyncError(syncErrors) == true }
            }
        )
        if (errors.isEmpty()) {
            tasksRepository.updateTask(
                Task(
                    id,
                    type,
                    TaskStatus(Status.SUCCESSFUL, "entities synced: ${entities.map { it.id }.joinToString { "," }}"),
                    OffsetDateTime.now()
                )
            )
        } else {
            tasksRepository.updateTask(
                Task(
                    id,
                    type,
                    TaskStatus(Status.ERROR, errors.joinToString(",\n") { it.toString() }),
                    OffsetDateTime.now()
                )
            )
        }
    }
}

class CacheLolDataTaskRunner(
    tasksRepository: TasksRepository,
    entitiesService: EntitiesService,
    entitySynchronizerProvider: EntitySynchronizerProvider
) : CacheGameDataTaskRunner(Game.LOL, tasksRepository, entitiesService, entitySynchronizerProvider) {
    override val type = TaskType.CACHE_LOL_DATA_TASK
}

class CacheWowDataTaskRunner(
    tasksRepository: TasksRepository,
    entitiesService: EntitiesService,
    entitySynchronizerProvider: EntitySynchronizerProvider
) : CacheGameDataTaskRunner(Game.WOW, tasksRepository, entitiesService, entitySynchronizerProvider) {
    override val type = TaskType.CACHE_WOW_DATA_TASK
}

class CacheWowHcDataTaskRunner(
    tasksRepository: TasksRepository,
    entitiesService: EntitiesService,
    entitySynchronizerProvider: EntitySynchronizerProvider
) : CacheGameDataTaskRunner(Game.WOW_HC, tasksRepository, entitiesService, entitySynchronizerProvider) {
    override val type = TaskType.CACHE_WOW_HC_DATA_TASK
}
