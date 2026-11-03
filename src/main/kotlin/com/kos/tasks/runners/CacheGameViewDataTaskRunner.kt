package com.kos.tasks.runners

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import arrow.core.raise.ensureNotNull
import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.entities.sync.EntitySynchronizerProvider
import com.kos.tasks.TaskCompleted
import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskType
import com.kos.views.ViewsService
import java.time.OffsetDateTime

class CacheGameViewDataTaskRunner(
    private val viewsService: ViewsService,
    private val entitiesService: EntitiesService,
    private val entitySynchronizerProvider: EntitySynchronizerProvider,
    private val cooldownSeconds: Long
) : TaskRunner, WithLogger("cacheGameViewDataTaskRunner") {

    override val type = TaskType.CACHE_GAME_VIEW_DATA_TASK

    override suspend fun run(arguments: Map<String, String>?): Either<TaskFailed, TaskCompleted> = either {
        val viewId = ensureNotNull(arguments?.get("viewId")) { TaskFailed("viewId argument is required") }
        val view = ensureNotNull(viewsService.getSimple(viewId)) { TaskFailed("view $viewId not found") }
        view.lastSyncedAt?.let { lastSyncedAt ->
            val nextAllowedAt = lastSyncedAt.plusSeconds(cooldownSeconds)
            ensure(OffsetDateTime.now().isAfter(nextAllowedAt)) {
                TaskFailed("view ${view.id} was synced recently", retryAfter = nextAllowedAt)
            }
        }

        logger.info("Running $type for game=${view.game} viewId=${view.id}")
        val entities = view.entitiesIds.mapNotNull { entitiesService.get(it, view.game) }
        val errors = entitySynchronizerProvider.synchronizerFor(view.game).synchronize(entities)
        ensure(errors.isEmpty()) { TaskFailed(errors.joinToString(",\n") { it.error() }) }

        val now = OffsetDateTime.now()
        viewsService.updateLastSyncedAt(view.id, now)
        TaskCompleted(retryAfter = now.plusSeconds(cooldownSeconds))
    }
}
