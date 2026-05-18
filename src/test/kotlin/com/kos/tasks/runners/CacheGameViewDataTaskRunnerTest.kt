package com.kos.tasks.runners

import com.kos.clients.riot.RiotClient
import com.kos.credentials.CredentialsService
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.datacache.EntitySynchronizerProvider
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntitySynchronizer
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.views.Game
import com.kos.views.SimpleView
import com.kos.views.ViewsService
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CacheGameViewDataTaskRunnerTest {

    private val tasksRepo = TasksInMemoryRepository()
    private val viewsRepo = ViewsInMemoryRepository()
    private val riotClient = mock(RiotClient::class.java)
    private val dataCacheRepo = DataCacheInMemoryRepository()
    private val entitiesRepo = EntitiesInMemoryRepository()
    private val entitiesService = EntitiesService(
        entitiesRepo,
        WowGuildsInMemoryRepository(),
        EntityResolverProvider(listOf(LolEntityResolver(entitiesRepo, riotClient))),
        mock(),
        mock()
    )
    private val entitySynchronizerProvider = EntitySynchronizerProvider(
        listOf(LolEntitySynchronizer(dataCacheRepo, riotClient))
    )
    private val viewsService = ViewsService(
        viewsRepo,
        entitiesService,
        mock(),
        CredentialsService(CredentialsInMemoryRepository()),
        EventStoreInMemory()
    )
    private val cooldownSeconds = 300L
    private val runner = CacheGameViewDataTaskRunner(
        tasksRepo, viewsService, entitiesService, entitySynchronizerProvider, cooldownSeconds
    )

    private val lolView = SimpleView("test-view", "Test View", "sanxei", false, emptyList(), Game.LOL, false)

    @Test
    fun `view entities are synced and task is recorded as successful`() = runBlocking {
        viewsRepo.withState(ViewsState(listOf(lolView), emptyList()))
        val id = UUID.randomUUID().toString()

        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, mapOf("viewId" to lolView.id))

        val task = tasksRepo.state().first()
        assertEquals(id, task.id)
        assertEquals(TaskType.CACHE_GAME_VIEW_DATA_TASK, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
        assertTrue(task.taskStatus.retryAfter != null)
    }

    @Test
    fun `task fails when viewId argument is missing`() = runBlocking {
        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, null)

        val task = tasksRepo.state().first()
        assertEquals(id, task.id)
        assertEquals(Status.ERROR, task.taskStatus.status)
        assertEquals("viewId argument is required", task.taskStatus.message)
    }

    @Test
    fun `task fails when view does not exist`() = runBlocking {
        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, mapOf("viewId" to "non-existent"))

        val task = tasksRepo.state().first()
        assertEquals(id, task.id)
        assertEquals(Status.ERROR, task.taskStatus.status)
        assertEquals("view non-existent not found", task.taskStatus.message)
    }

    @Test
    fun `task fails with retryAfter when view synced recently`(): Unit = runBlocking {
        val recentlySynced = lolView.copy(lastSyncedAt = OffsetDateTime.now().minusSeconds(10))
        viewsRepo.withState(ViewsState(listOf(recentlySynced), emptyList()))
        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, mapOf("viewId" to recentlySynced.id))

        val task = tasksRepo.state().first()
        assertEquals(id, task.id)
        assertEquals(Status.ERROR, task.taskStatus.status)
        assertNotNull(task.taskStatus.retryAfter)
    }
}
