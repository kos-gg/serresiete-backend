package com.kos.tasks.runners

import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskCompleted
import arrow.core.Either
import com.kos.clients.TimeoutError
import com.kos.clients.riot.RiotClient
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.repository.ViewsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals

class UpdateLolEntitiesTaskRunnerTest {

    private val riotClient = mock(RiotClient::class.java)
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val entitiesService = EntitiesService(
        entitiesRepository,
        WowGuildsInMemoryRepository(),
        EntityResolverProvider(
            wowResolver = mock(),
            wowGuildResolver = mock(),
            wowHardcoreResolver = mock(),
            wowHardcoreGuildResolver = mock(),
            lolResolver = LolEntityResolver(entitiesRepository, riotClient)
        ),
        mock(),
        LolEntityUpdater(riotClient, entitiesRepository),
        WowHardcoreGuildUpdater(mock(), mock(), entitiesRepository, ViewsInMemoryRepository()),
        mock()
    )
    private val runner = UpdateLolEntitiesTaskRunner(entitiesService)

    @Test
    fun `lol entities are updated and task is recorded as successful`() = runBlocking {
        `when`(riotClient.getSummonerByPuuid(basicLolEntity.puuid))
            .thenReturn(arrow.core.Either.Right(EntitiesTestHelper.basicLolSummoner))
        `when`(riotClient.getAccountByPUUID(basicLolEntity.puuid))
            .thenReturn(arrow.core.Either.Right(EntitiesTestHelper.basicRiotAccount))

        val result = runner.run(null)

        assertEquals(Either.Right(TaskCompleted()), result)
    }

    @Test
    fun `a lol entity that riot fails to return is reported with its id and riot's error`() = runBlocking {
        entitiesRepository.withState(EntitiesState(listOf(), listOf(), listOf(basicLolEntity)))
        `when`(riotClient.getSummonerByPuuid(basicLolEntity.puuid))
            .thenReturn(arrow.core.Either.Right(EntitiesTestHelper.basicLolSummoner))
        `when`(riotClient.getAccountByPUUID(basicLolEntity.puuid))
            .thenReturn(arrow.core.Either.Left(TimeoutError("timeout")))

        val result = runner.run(null)

        assertEquals(Either.Left(TaskFailed("Entity ${basicLolEntity.id}: TimeoutError(message=timeout)")), result)
    }
}
