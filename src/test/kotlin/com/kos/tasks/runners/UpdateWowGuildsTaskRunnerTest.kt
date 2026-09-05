package com.kos.tasks.runners

import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.domain.WowCharacterResponse
import com.kos.clients.domain.WowGuildResponse
import com.kos.clients.domain.WowMemberResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.entities.EntitiesService
import com.kos.entities.EntityResolverProvider
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.GuildPayload
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.entities.repository.wowguilds.WowGuildsState
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wow.WowGuildUpdater
import com.kos.tasks.Status
import com.kos.tasks.Task
import com.kos.tasks.TaskStatus
import com.kos.tasks.TaskType
import com.kos.tasks.repository.TasksInMemoryRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateWowGuildsTaskRunnerTest {

    private val blizzardClient = mock(BlizzardClient::class.java)
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val wowGuildsRepository = WowGuildsInMemoryRepository()
    private val viewsRepository = ViewsInMemoryRepository()
    private val wowResolver = WowEntityResolver(entitiesRepository, raiderIoClient, blizzardClient)
    private val entitiesService = EntitiesService(
        entitiesRepository,
        wowGuildsRepository,
        EntityResolverProvider(wowResolver = wowResolver, wowHardcoreResolver = mock(), lolResolver = mock()),
        mock(),
        mock(),
        WowGuildUpdater(wowResolver, entitiesRepository, viewsRepository)
    )
    private val tasksRepo = TasksInMemoryRepository()
    private val runner = UpdateWowGuildsTaskRunner(tasksRepo, entitiesService)

    @Test
    fun `wow guilds are updated and task is recorded as successful`() = runBlocking {
        val realm = "twisting-nether"
        val guild = "Method"
        val region = "eu"
        val blizzardId: Long = 1
        val character = "kakarona"
        val viewId = "1"

        viewsRepository.create(viewId, "guild view", "sanxei", listOf(), Game.WOW, false, null)
        wowGuildsRepository.withState(
            WowGuildsState(listOf(Triple(GuildPayload(guild, realm, region, blizzardId), viewId, Game.WOW)))
        )

        `when`(blizzardClient.getRetailGuildRoster(region, realm, guild))
            .thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(WowMemberResponse(WowCharacterResponse(character, 90))),
                        WowGuildResponse(blizzardId)
                    )
                )
            )
        `when`(raiderIoClient.getScore(WowEntityRequest(character, region, realm)))
            .thenReturn(Either.Right(1500.0))

        val id = UUID.randomUUID().toString()
        tasksRepo.insertTask(Task(id, runner.type, TaskStatus(Status.PENDING, null), OffsetDateTime.now()))

        runner.run(id, null)

        val task = tasksRepo.state().first()
        assertEquals(id, task.id)
        assertEquals(TaskType.UPDATE_WOW_GUILDS, task.type)
        assertEquals(Status.SUCCESSFUL, task.taskStatus.status)

        val updatedView = viewsRepository.get(viewId)!!
        val characterRequest: EntityRequest = WowEntityRequest(character, region, realm)
        val characterEntity = entitiesRepository.get(characterRequest, Game.WOW)!!
        assertEquals(listOf(characterEntity.id), updatedView.entitiesIds)
    }
}
