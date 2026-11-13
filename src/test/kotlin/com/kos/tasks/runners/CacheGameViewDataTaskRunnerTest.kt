package com.kos.tasks.runners

import com.kos.tasks.TaskFailed
import com.kos.tasks.TaskCompleted
import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.credentials.CredentialsService
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.datacache.TestHelper.wowHardcoreDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.entities.sync.EntitySynchronizerProvider
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntitySynchronizer
import com.kos.sources.wowhc.WowHardcoreEntitySynchronizer
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.views.Game
import com.kos.views.SimpleView
import com.kos.views.ViewsService
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class CacheGameViewDataTaskRunnerTest {

    private val viewsRepo = ViewsInMemoryRepository()
    private val riotClient = mock(RiotClient::class.java)
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val wowItemsDatabaseRepository = mock(WowItemsDatabaseRepository::class.java)
    private val dataCacheRepo = DataCacheInMemoryRepository()
    private val entitiesRepo = EntitiesInMemoryRepository()
    private val entitiesService = EntitiesService(
        entitiesRepo,
        WowGuildsInMemoryRepository(),
        EntityResolverProvider(
            wowResolver = mock(),
            wowGuildResolver = mock(),
            wowHardcoreResolver = mock(),
            wowHardcoreGuildResolver = mock(),
            lolResolver = LolEntityResolver(entitiesRepo, riotClient),
            wowForeverResolver = mock(),
            wowForeverGuildResolver = mock()
        ),
        mock(),
        mock(),
        mock(),
        mock(),
        mock(),
        mock()
    )
    private val entitySynchronizerProvider = EntitySynchronizerProvider(
        wowSynchronizer = mock(),
        wowHardcoreSynchronizer = WowHardcoreEntitySynchronizer(
            dataCacheRepo,
            entitiesRepo,
            raiderIoClient,
            blizzardClient,
            wowItemsDatabaseRepository
        ),
        lolSynchronizer = LolEntitySynchronizer(dataCacheRepo, riotClient),
        wowForeverSynchronizer = mock()
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
        viewsService, entitiesService, entitySynchronizerProvider, cooldownSeconds
    )

    private val lolView = SimpleView("test-view", "Test View", "sanxei", false, emptyList(), Game.LOL, false)
    private val wowHcView =
        SimpleView("wohc-view", "WowHC View", "sanxei", false, listOf(basicWowHardcoreEntity.id), Game.WOW_HC, false)

    @Test
    fun `view entities are synced and task is recorded as successful`() = runBlocking {
        viewsRepo.withState(ViewsState(listOf(lolView), emptyList()))

        val result = runner.run(mapOf("viewId" to lolView.id))

        assertNotNull(result.getOrNull()?.retryAfter)
    }

    @Test
    fun `task fails when viewId argument is missing`() = runBlocking {

        val result = runner.run(null)

        assertEquals(Either.Left(TaskFailed("viewId argument is required")), result)
    }

    @Test
    fun `task fails when view does not exist`() = runBlocking {

        val result = runner.run(mapOf("viewId" to "non-existent"))

        assertEquals(Either.Left(TaskFailed("view non-existent not found")), result)
    }

    @Test
    fun `task fails with retryAfter when view synced recently`(): Unit = runBlocking {
        val recentlySynced = lolView.copy(lastSyncedAt = OffsetDateTime.now().minusSeconds(10))
        viewsRepo.withState(ViewsState(listOf(recentlySynced), emptyList()))

        val result = runner.run(mapOf("viewId" to recentlySynced.id))

        assertNotNull(result.leftOrNull()?.retryAfter)
    }

    @Test
    fun `dead wow hardcore entities are non-fatal and task is recorded as successful`() = runBlocking {
        entitiesRepo.withState(EntitiesState(listOf(), listOf(basicWowHardcoreEntity), listOf()))
        dataCacheRepo.withState(
            listOf(
                wowHardcoreDataCache.copy(
                    data = wowHardcoreDataCache.data.replace(
                        """"isDead": false""",
                        """"isDead": true"""
                    )
                )
            )
        )
        viewsRepo.withState(ViewsState(listOf(wowHcView), emptyList()))

        val result = runner.run(mapOf("viewId" to wowHcView.id))

        assertTrue(result.isRight())
    }
}
