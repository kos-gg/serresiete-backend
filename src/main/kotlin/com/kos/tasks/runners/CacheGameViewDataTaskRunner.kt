package com.kos.tasks.runners

import arrow.core.raise.either
import arrow.core.raise.ensure
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
import com.kos.views.SimpleView
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
        either<TaskStatus, SimpleView> {
            val viewId = arguments?.get("viewId")
                ?: raise(TaskStatus(Status.ERROR, "viewId argument is required"))
            val view = viewsService.getSimple(viewId)
                ?: raise(TaskStatus(Status.ERROR, "view $viewId not found"))
            view.lastSyncedAt?.let { lastSyncedAt ->
                val nextAllowedAt = lastSyncedAt.plusSeconds(cooldownSeconds)
                ensure(OffsetDateTime.now().isAfter(nextAllowedAt)) {
                    TaskStatus(Status.ERROR, "view ${view.id} was synced recently", retryAfter = nextAllowedAt)
                }
            }
            view
        }.fold(
            { errorTaskStatus ->
                tasksRepository.updateTask(Task(id, type, errorTaskStatus, OffsetDateTime.now()))
            },
            { view ->
                logger.info("Running $type for game=${view.game} viewId=${view.id}")
                val entities = view.entitiesIds.mapNotNull { entitiesService.get(it, view.game) }
                val synchronizer = entitySynchronizerProvider.synchronizerFor(view.game)
                val errors = synchronizer.fold(
                    left = { listOf(SynchronizerNotFound(view.game)) },
                    right = { it.synchronize(entities)
                        .filter { syncErrors -> synchronizer?.isSyncError(syncErrors) == true }}
                )
                if (errors.isEmpty()) {
                    val syncedAt = OffsetDateTime.now()
                    viewsService.updateLastSyncedAt(view.id, syncedAt)
                    tasksRepository.updateTask(
                        Task(
                            id,
                            type,
                            TaskStatus(
                                Status.SUCCESSFUL,
                                "entities synced for view ${view.id}",
                                retryAfter = syncedAt.plusSeconds(cooldownSeconds)
                            ),
                            syncedAt
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
        )
    }
}
