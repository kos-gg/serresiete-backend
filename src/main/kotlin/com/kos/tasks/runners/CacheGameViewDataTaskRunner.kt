package com.kos.tasks.runners

import com.kos.common.WithLogger
import com.kos.common.error.SynchronizerNotFound
import com.kos.common.error.WowHardcoreCharacterIsDead
import com.kos.common.fold
import com.kos.datacache.EntitySynchronizerProvider
import com.kos.entities.EntitiesService
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksRepository
import com.kos.views.ViewsService
import java.time.OffsetDateTime

class CacheGameViewDataTaskRunner(
    private val tasksRepository: TasksRepository,
    private val viewsService: ViewsService,
    private val entitiesService: EntitiesService,
    private val entitySynchronizerProvider: EntitySynchronizerProvider
) : TaskRunner, WithLogger("cacheGameViewDataTaskRunner") {

    override val type = TaskType.CACHE_GAME_VIEW_DATA_TASK

    override suspend fun run(id: String, arguments: Map<String, String>?) {
        val viewId = arguments?.get("viewId")
        if (viewId == null) {
            tasksRepository.insertTask(Task(id, type, TaskStatus(Status.ERROR, "viewId argument is required"), OffsetDateTime.now()))
            return
        }
        val view = viewsService.getSimple(viewId)
        if (view == null) {
            tasksRepository.insertTask(Task(id, type, TaskStatus(Status.ERROR, "view $viewId not found"), OffsetDateTime.now()))
            return
        }
        val game = view.game
        logger.info("Running $type for game=$game viewId=$viewId")
        val entities = view.entitiesIds.mapNotNull { entitiesService.get(it, game) }
        val errors = entitySynchronizerProvider.synchronizerFor(game).fold(
            left = { listOf(SynchronizerNotFound(game)) },
            right = { it.synchronize(entities) }
        )
        if (errors.isEmpty() || errors.all { it is WowHardcoreCharacterIsDead }) {
            tasksRepository.insertTask(Task(id, type, TaskStatus(Status.SUCCESSFUL, "entities synced for view $viewId"), OffsetDateTime.now()))
        } else {
            tasksRepository.insertTask(Task(id, type, TaskStatus(Status.ERROR, errors.joinToString(",\n") { it.toString() }), OffsetDateTime.now()))
        }
    }
}
