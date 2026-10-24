package com.kos.tasks.runners

import com.kos.common.WithLogger
import com.kos.entities.sync.EntitySynchronizerProvider
import com.kos.entities.sync.SyncEntitySelector
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskError
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository
import com.kos.views.Game
import java.time.OffsetDateTime

class CacheGameDataTaskRunner(
    private val game: Game,
    override val type: TaskType,
    private val tasksRepository: TasksRepository,
    private val syncEntitySelector: SyncEntitySelector,
    private val entitySynchronizerProvider: EntitySynchronizerProvider
) : TaskRunner, WithLogger("cacheGameDataTaskRunner") {

    override suspend fun run(id: String, arguments: Map<String, String>?) {
        logger.info("Running $type")
        val entities = syncEntitySelector.select(game)
        logger.debug("entities to be synced: {}", entities.map { it.id }.joinToString(","))
        val errors = entitySynchronizerProvider.synchronizerFor(game).synchronize(entities)

        val task = if (errors.isEmpty())
            Task(id, type, TaskStatus(Status.SUCCESSFUL), OffsetDateTime.now())
        else
            Task(
                id,
                type,
                TaskStatus(Status.ERROR),
                OffsetDateTime.now(),
                TaskError(arguments, errors.joinToString(",\n") { it.error() })
            )

        tasksRepository.updateTask(task)
    }
}
