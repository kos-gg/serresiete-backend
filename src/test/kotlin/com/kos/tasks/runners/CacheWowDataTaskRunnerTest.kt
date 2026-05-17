package com.kos.tasks.runners

import arrow.core.Either
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.raiderio.RaiderIoHttpClientHelper
import com.kos.clients.raiderio.RaiderIoHttpClientHelper.runDetails
import com.kos.datacache.EntitySynchronizerProvider
import com.kos.datacache.RaiderIoMockHelper
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wow.WowEntitySynchronizer
import com.kos.sources.wow.staticdata.wowseason.WowSeason
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonsState
import com.kos.tasks.Status
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheWowDataTaskRunnerTest {

    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val dataCacheRepo = DataCacheInMemoryRepository()
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val wowSeasonsRepository = WowSeasonInMemoryRepository()
    private val entitiesService = EntitiesService(
        entitiesRepository,
        WowGuildsInMemoryRepository(),
        EntityResolverProvider(listOf(WowEntityResolver(entitiesRepository, raiderIoClient))),
        mock(),
        mock()
    )
    private val entitySynchronizerProvider = EntitySynchronizerProvider(
        listOf(WowEntitySynchronizer(dataCacheRepo, raiderIoClient, wowSeasonsRepository))
    )
    private val tasksRepo = TasksInMemoryRepository()
    private val runner = CacheWowDataTaskRunner(tasksRepo, entitiesService, entitySynchronizerProvider)

    @Test
    fun `wow entities are synced and task is recorded as successful`() = runBlocking {
        entitiesRepository.withState(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))
        val season = WowSeason(1, "Default Season", "default-season", 1, "", true)
        wowSeasonsRepository.withState(WowSeasonsState(listOf(season)))

        val run = RaiderIoHttpClientHelper.mythicPlusRun
        `when`(raiderIoClient.get(basicWowEntity)).thenReturn(RaiderIoMockHelper.get(basicWowEntity))
        `when`(raiderIoClient.cutoff(season.slug)).thenReturn(RaiderIoMockHelper.cutoff())
        `when`(raiderIoClient.getRunDetails(season.slug, run.runId.toString())).thenReturn(Either.Right(runDetails))

        val id = UUID.randomUUID().toString()

        runner.run(id, null)

        val task = tasksRepo.state().first()
        assertEquals(1, dataCacheRepo.state().size)
        assertEquals(id, task.id)
        assertEquals(TaskType.CACHE_WOW_DATA_TASK, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
    }
}
