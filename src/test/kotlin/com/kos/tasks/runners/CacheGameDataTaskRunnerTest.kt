package com.kos.tasks.runners

import arrow.core.Either
import com.kos.clients.domain.QueueType
import com.kos.clients.riot.RiotClient
import com.kos.common.error.SyncProcessingError
import com.kos.datacache.RiotMockHelper
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.sync.EntitySynchronizer
import com.kos.entities.sync.EntitySynchronizerProvider
import com.kos.entities.sync.SyncBudget
import com.kos.entities.sync.SyncEntitySelector
import com.kos.entities.sync.rules.StalenessSyncRule
import com.kos.sources.lol.LolEntitySynchronizer
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

class CacheGameDataTaskRunnerTest {

    private val riotClient = mock(RiotClient::class.java)
    private val dataCacheRepo = DataCacheInMemoryRepository()
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val tasksRepo = TasksInMemoryRepository()
    private fun syncEntitySelector(entitiesRepository: EntitiesRepository) = SyncEntitySelector(
        StalenessSyncRule(
            entitiesRepository,
            30,
            SyncBudget(mapOf(Game.LOL to Int.MAX_VALUE, Game.WOW to Int.MAX_VALUE, Game.WOW_HC to Int.MAX_VALUE))
        )
    )

    @Test
    fun `entities are synced and task is recorded as successful`() = runBlocking {
        entitiesRepository.withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
        val runner = CacheGameDataTaskRunner(
            Game.LOL,
            TaskType.CACHE_LOL_DATA_TASK,
            tasksRepo,
            syncEntitySelector(entitiesRepository),
            EntitySynchronizerProvider(
                wowSynchronizer = mock(),
                wowHardcoreSynchronizer = mock(),
                lolSynchronizer = LolEntitySynchronizer(dataCacheRepo, riotClient)
            )
        )

        `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid)).thenReturn(RiotMockHelper.leagueEntries)
        `when`(
            riotClient.getMatchesByPuuid(
                basicLolEntity.puuid,
                QueueType.SOLO_Q.toInt()
            )
        ).thenReturn(RiotMockHelper.matches)
        `when`(
            riotClient.getMatchesByPuuid(
                basicLolEntity.puuid,
                QueueType.FLEX_Q.toInt()
            )
        ).thenReturn(RiotMockHelper.matches)
        `when`(riotClient.getMatchById(RiotMockHelper.matchId)).thenReturn(Either.Right(RiotMockHelper.match))

        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, null)

        val task = tasksRepo.state().first()
        assertEquals(1, dataCacheRepo.state().size)
        assertEquals(id, task.id)
        assertEquals(TaskType.CACHE_LOL_DATA_TASK, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
    }

    @Test
    fun `task is recorded as error and the synchronizer's errors are stored when synchronization fails`() =
        runBlocking {
            entitiesRepository.withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
            val failingSynchronizer = mock(EntitySynchronizer::class.java)
            `when`(failingSynchronizer.synchronize(listOf(basicLolEntity)))
                .thenReturn(listOf(SyncProcessingError("LOL", "boom")))
            val runner = CacheGameDataTaskRunner(
                Game.LOL,
                TaskType.CACHE_LOL_DATA_TASK,
                tasksRepo,
                syncEntitySelector(entitiesRepository),
                EntitySynchronizerProvider(
                    wowSynchronizer = mock(),
                    wowHardcoreSynchronizer = mock(),
                    lolSynchronizer = failingSynchronizer
                )
            )

            val id = UUID.randomUUID().toString()
            tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

            runner.run(id, null)

            val task = tasksRepo.state().first()
            assertEquals(Status.ERROR, task.taskStatus.status)
            assertEquals("SyncProcessingError(type=LOL, message=boom)", task.taskStatus.message)
        }
}
