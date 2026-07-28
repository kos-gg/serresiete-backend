package com.kos.tasks.runners

import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.RaiderioWowHeadEmbeddedResponse
import com.kos.clients.domain.TalentLoadout
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.TestHelper.wowHardcoreDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.sync.EntitySynchronizerProvider
import com.kos.entities.sync.SyncEntitySelector
import com.kos.entities.sync.rules.StalenessSyncRule
import com.kos.entities.sync.SyncBudget
import com.kos.sources.wowhc.WowHardcoreEntitySynchronizer
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheWowHcDataTaskRunnerTest {

    private val blizzardClient = mock(BlizzardClient::class.java)
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val wowItemsDatabaseRepository = mock(WowItemsDatabaseRepository::class.java)
    private val dataCacheRepo = DataCacheInMemoryRepository()
    private val entitiesRepository = EntitiesInMemoryRepository(dataCacheRepo)
    private val syncEntitySelector = SyncEntitySelector(
        StalenessSyncRule(
            entitiesRepository,
            30,
            SyncBudget(mapOf(Game.LOL to Int.MAX_VALUE, Game.WOW to Int.MAX_VALUE, Game.WOW_HC to Int.MAX_VALUE))
        )
    )
    private val entitySynchronizerProvider = EntitySynchronizerProvider(
        listOf(
            WowHardcoreEntitySynchronizer(
                dataCacheRepo,
                entitiesRepository,
                raiderIoClient,
                blizzardClient,
                wowItemsDatabaseRepository
            )
        )
    )
    private val tasksRepo = TasksInMemoryRepository()
    private val runner = CacheWowHcDataTaskRunner(tasksRepo, syncEntitySelector, entitySynchronizerProvider)

    @Test
    fun `wow hc entities are synced and task is recorded as successful`() = runBlocking {
        entitiesRepository.withState(EntitiesState(listOf(), listOf(basicWowHardcoreEntity), listOf()))

        `when`(
            blizzardClient.getCharacterProfile(
                basicWowHardcoreEntity.region,
                basicWowHardcoreEntity.realm,
                basicWowHardcoreEntity.name
            )
        )
            .thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowHardcoreEntity).map { it.copy(id = 12345) })
        `when`(
            blizzardClient.getCharacterMedia(
                basicWowHardcoreEntity.region,
                basicWowHardcoreEntity.realm,
                basicWowHardcoreEntity.name
            )
        )
            .thenReturn(BlizzardMockHelper.getCharacterMedia(basicWowHardcoreEntity))
        `when`(
            blizzardClient.getCharacterEquipment(
                basicWowHardcoreEntity.region,
                basicWowHardcoreEntity.realm,
                basicWowHardcoreEntity.name
            )
        )
            .thenReturn(BlizzardMockHelper.getCharacterEquipment())
        `when`(
            blizzardClient.getCharacterStats(
                basicWowHardcoreEntity.region,
                basicWowHardcoreEntity.realm,
                basicWowHardcoreEntity.name
            )
        )
            .thenReturn(BlizzardMockHelper.getCharacterStats())
        `when`(
            blizzardClient.getCharacterSpecializations(
                basicWowHardcoreEntity.region,
                basicWowHardcoreEntity.realm,
                basicWowHardcoreEntity.name
            )
        )
            .thenReturn(BlizzardMockHelper.getCharacterSpecializations())
        `when`(
            blizzardClient.getItem(
                basicWowHardcoreEntity.region,
                18421
            )
        ).thenReturn(BlizzardMockHelper.getWowItemResponse())
        `when`(
            blizzardClient.getItemMedia(
                basicWowHardcoreEntity.region,
                18421
            )
        ).thenReturn(BlizzardMockHelper.getItemMedia())
        `when`(
            raiderIoClient.wowheadEmbeddedCalculator(
                basicWowHardcoreEntity.region,
                basicWowHardcoreEntity.realm,
                basicWowHardcoreEntity.name
            )
        )
            .thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))

        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, null)

        val task = tasksRepo.state().first()
        assertEquals(1, dataCacheRepo.state().size)
        assertEquals(id, task.id)
        assertEquals(TaskType.CACHE_WOW_HC_DATA_TASK, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
    }

    @Test
    fun `dead wow hardcore entities are non-fatal and task is recorded as successful`() = runBlocking {
        entitiesRepository.withState(EntitiesState(listOf(), listOf(basicWowHardcoreEntity), listOf()))
        dataCacheRepo.withState(
            listOf(
                wowHardcoreDataCache.copy(
                    inserted = OffsetDateTime.now().minusMinutes(31),
                    data = wowHardcoreDataCache.data.replace(
                        """"isDead": false""",
                        """"isDead": true"""
                    )
                )
            )
        )

        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, null)

        val task = tasksRepo.state().first()
        assertEquals(id, task.id)
        assertEquals(TaskType.CACHE_WOW_HC_DATA_TASK, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
        assertEquals(1, dataCacheRepo.state().size)
    }
}
