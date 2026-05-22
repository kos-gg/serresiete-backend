package com.kos.tasks.runners

import arrow.core.Either
import com.kos.clients.domain.riot.QueueType
import com.kos.clients.riot.RiotClient
import com.kos.datacache.EntitySynchronizerProvider
import com.kos.datacache.RiotMockHelper
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntitySynchronizer
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class CacheLolDataTaskRunnerTest {

    private val riotClient = mock(RiotClient::class.java)
    private val dataCacheRepo = DataCacheInMemoryRepository()
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val entitiesService = EntitiesService(
        entitiesRepository,
        WowGuildsInMemoryRepository(),
        EntityResolverProvider(listOf(LolEntityResolver(entitiesRepository, riotClient))),
        mock(),
        mock()
    )
    private val entitySynchronizerProvider = EntitySynchronizerProvider(
        listOf(LolEntitySynchronizer(dataCacheRepo, riotClient))
    )
    private val tasksRepo = TasksInMemoryRepository()
    private val runner = CacheLolDataTaskRunner(tasksRepo, entitiesService, entitySynchronizerProvider)

    @Test
    fun `lol entities are synced and task is recorded as successful`() = runBlocking {
        entitiesRepository.withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))

        `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid)).thenReturn(RiotMockHelper.leagueEntries)
        `when`(riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.SOLO_Q.toInt())).thenReturn(RiotMockHelper.matches)
        `when`(riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.FLEX_Q.toInt())).thenReturn(RiotMockHelper.matches)
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
}
