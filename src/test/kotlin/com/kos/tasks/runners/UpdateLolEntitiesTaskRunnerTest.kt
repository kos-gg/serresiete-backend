package com.kos.tasks.runners

import com.kos.clients.riot.RiotClient
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.views.repository.ViewsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateLolEntitiesTaskRunnerTest {

    private val riotClient = mock(RiotClient::class.java)
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val entitiesService = EntitiesService(
        entitiesRepository,
        WowGuildsInMemoryRepository(),
        EntityResolverProvider(listOf(LolEntityResolver(entitiesRepository, riotClient))),
        LolEntityUpdater(riotClient, entitiesRepository),
        WowHardcoreGuildUpdater(mock(), entitiesRepository, ViewsInMemoryRepository())
    )
    private val tasksRepo = TasksInMemoryRepository()
    private val runner = UpdateLolEntitiesTaskRunner(tasksRepo, entitiesService)

    @Test
    fun `lol entities are updated and task is recorded as successful`() = runBlocking {
        `when`(riotClient.getSummonerByPuuid(basicLolEntity.puuid))
            .thenReturn(arrow.core.Either.Right(EntitiesTestHelper.basicGetSummonerResponse))
        `when`(riotClient.getAccountByPUUID(basicLolEntity.puuid))
            .thenReturn(arrow.core.Either.Right(EntitiesTestHelper.basicGetAccountResponse))

        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, null)

        val task = tasksRepo.state().first()
        assertEquals(id, task.id)
        assertEquals(TaskType.UPDATE_LOL_ENTITIES_TASK, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)
    }
}
