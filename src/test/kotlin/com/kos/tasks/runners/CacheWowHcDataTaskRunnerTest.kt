package com.kos.tasks.runners

import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.datacache.EntitySynchronizerProvider
import com.kos.datacache.TestHelper.wowHardcoreDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.sources.wowhc.WowHardcoreEntitySynchronizer
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheWowHcDataTaskRunnerTest {

    private val blizzardClient = mock(BlizzardClient::class.java)
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val wowItemsDatabaseRepository = mock(WowItemsDatabaseRepository::class.java)
    private val dataCacheRepo = DataCacheInMemoryRepository()
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val entitiesService = EntitiesService(
        entitiesRepository,
        WowGuildsInMemoryRepository(),
        EntityResolverProvider(listOf()),
        mock(),
        mock()
    )
    private val entitySynchronizerProvider = EntitySynchronizerProvider(
        listOf(WowHardcoreEntitySynchronizer(dataCacheRepo, entitiesRepository, raiderIoClient, blizzardClient, wowItemsDatabaseRepository))
    )
    private val tasksRepo = TasksInMemoryRepository()
    private val runner = CacheWowHcDataTaskRunner(tasksRepo, entitiesService, entitySynchronizerProvider)

    @Test
    fun `dead wow hardcore entities are non-fatal and task is recorded as successful`() = runBlocking {
        entitiesRepository.withState(EntitiesState(listOf(), listOf(basicWowHardcoreEntity), listOf()))
        dataCacheRepo.withState(
            listOf(wowHardcoreDataCache.copy(data = wowHardcoreDataCache.data.replace(""""isDead": false""", """"isDead": true""")))
        )

        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, null)

        val task = tasksRepo.state().first()
        assertEquals(id, task.id)
        assertEquals(TaskType.CACHE_WOW_HC_DATA_TASK, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
    }
}
