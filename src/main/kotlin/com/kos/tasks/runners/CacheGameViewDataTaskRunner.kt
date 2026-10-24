package com.kos.tasks.runners

import arrow.core.raise.either
import arrow.core.raise.ensure
import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.entities.sync.EntitySynchronizerProvider
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskError
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository
import com.kos.views.ViewsService
import java.time.OffsetDateTime

class CacheGameViewDataTaskRunner(
    private val tasksRepository: TasksRepository,
    private val viewsService: ViewsService,
    private val entitiesService: EntitiesService,
    private val entitySynchronizerProvider: EntitySynchronizerProvider,
    private val cooldownSeconds: Long
) : TaskRunner, WithLogger("cacheGameViewDataTaskRunner") {

    override val type = TaskType.CACHE_GAME_VIEW_DATA_TASK

    override suspend fun run(id: String, arguments: Map<String, String>?) {
        either {
            val viewId = arguments?.get("viewId")
                ?: raise(failed(id, arguments, "viewId argument is required"))
            val view = viewsService.getSimple(viewId)
                ?: raise(failed(id, arguments, "view $viewId not found"))
            view.lastSyncedAt?.let { lastSyncedAt ->
                val nextAllowedAt = lastSyncedAt.plusSeconds(cooldownSeconds)
                ensure(OffsetDateTime.now().isAfter(nextAllowedAt)) {
                    failed(id, arguments, "view ${view.id} was synced recently", retryAfter = nextAllowedAt)
                }
            }
            view
        }.fold(
            { failedTask -> tasksRepository.updateTask(failedTask) },
            { view ->
                logger.info("Running $type for game=${view.game} viewId=${view.id}")
                val entities = view.entitiesIds.mapNotNull { entitiesService.get(it, view.game) }

                val errors = entitySynchronizerProvider.synchronizerFor(view.game).synchronize(entities)

                val now = OffsetDateTime.now()
                val task = if (errors.isEmpty()) {
                    viewsService.updateLastSyncedAt(view.id, now)
                    Task(id, type, TaskStatus(Status.SUCCESSFUL, retryAfter = now.plusSeconds(cooldownSeconds)), now)
                } else
                    failed(id, arguments, errors.joinToString(",\n") { it.error() })

                tasksRepository.updateTask(task)
            }
        )
    }

    private fun failed(
        id: String,
        arguments: Map<String, String>?,
        detail: String,
        retryAfter: OffsetDateTime? = null
    ): Task =
        Task(id, type, TaskStatus(Status.ERROR, retryAfter), OffsetDateTime.now(), TaskError(arguments, detail))
}
