package com.kos.tasks.runners

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.common.WithLogger
import com.kos.entities.sync.EntitySynchronizerProvider
import com.kos.entities.sync.SyncEntitySelector
import com.kos.tasks.TaskCompleted
import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskType
import com.kos.views.Game

class CacheGameDataTaskRunner(
    private val game: Game,
    override val type: TaskType,
    private val syncEntitySelector: SyncEntitySelector,
    private val entitySynchronizerProvider: EntitySynchronizerProvider
) : TaskRunner, WithLogger("cacheGameDataTaskRunner") {

    override suspend fun run(arguments: Map<String, String>?): Either<TaskFailed, TaskCompleted> = either {
        logger.info("Running $type")
        val entities = syncEntitySelector.select(game)
        logger.debug("entities to be synced: {}", entities.map { it.id }.joinToString(","))
        val errors = entitySynchronizerProvider.synchronizerFor(game).synchronize(entities)
        ensure(errors.isEmpty()) { TaskFailed(errors.joinToString(",\n") { it.error() }) }
        TaskCompleted()
    }
}
