package com.kos.eventsourcing.subscriptions.sync

import org.mockito.Mockito.mock
import com.kos.assertTrue
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.riot.RiotClient
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.subscriptions.EventProcessOutcome.Processed
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wow.WowGuildUpdater
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.Game
import com.kos.views.ViewEntity
import com.kos.views.ViewsEventService
import com.kos.views.ViewsTestHelper
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsRepository
import com.kos.views.repository.ViewsState
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import com.kos.sources.wow.WowGuildResolver
import com.kos.sources.wowhc.WowHardcoreGuildResolver
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository

class ViewsSyncProcessorTest {
    private val riotClient = Mockito.mock(RiotClient::class.java)
    private val blizzardClient = Mockito.mock(BlizzardClient::class.java)

    private val aggregateRoot = "/credentials/owner"
    private val operationId = "operation-distinct-from-view-id"

    @Test
    fun `viewsProcessor calls createView on VIEW_TO_BE_CREATED event, creates a view and emits its completion event`() {
        runBlocking {
            val (viewsEventService, viewsRepository) = createService(
                ViewsState(listOf(), listOf()),
                EntitiesTestHelper.emptyEntitiesState
            )

            val spiedService = spyk(viewsEventService)

            val eventData =
                ViewToBeCreatedEvent(ViewsTestHelper.id, "name", true, listOf(), Game.LOL, "owner", false, null)
            val eventWithVersion = EventWithVersion(
                1L,
                Event(aggregateRoot, operationId, eventData)
            )

            val outcome = ViewsEventProcessor(eventWithVersion, spiedService).process()
            coVerify { spiedService.createView(eq(eventData)) }

            assertEquals(
                Processed(
                    ViewCreatedEvent(
                        ViewsTestHelper.id,
                        ViewsTestHelper.name,
                        ViewsTestHelper.owner, listOf(), true, Game.LOL, false, null
                    )
                ),
                outcome
            )

            assertView(viewsRepository, ViewsTestHelper.name)
        }
    }

    @Test
    fun `viewsProcessor calls edit view on VIEW_TO_BE_EDITED event, edits a view and emits its completion event`() {
        runBlocking {
            val (viewsEventService, viewsRepository) = createService(
                ViewsState(
                    listOf(ViewsTestHelper.basicSimpleLolView),
                    ViewsTestHelper.basicSimpleLolView.entitiesIds.map {
                        ViewEntity(
                            it,
                            ViewsTestHelper.basicSimpleLolView.id,
                            "alias"
                        )
                    }),
                EntitiesTestHelper.emptyEntitiesState
            )

            val spiedService = spyk(viewsEventService)

            val newName = "new-name"
            val eventData = ViewToBeEditedEvent(ViewsTestHelper.id, newName, true, listOf(), Game.LOL, false)
            val eventWithVersion = EventWithVersion(
                1L,
                Event(aggregateRoot, operationId, eventData)
            )

            val outcome = ViewsEventProcessor(eventWithVersion, spiedService).process()
            coVerify { spiedService.editView(eq(eventData)) }

            assertEquals(
                Processed(
                    ViewEditedEvent(ViewsTestHelper.id, newName, listOf(), true, Game.LOL, false)
                ),
                outcome
            )

            assertView(viewsRepository, newName)
        }
    }

    @Test
    fun `viewsProcessor calls patch view on VIEW_TO_BE_PATCHED event, patches a view and emits its completion event`() {
        runBlocking {
            val (viewsEventService, viewsRepository) = createService(
                ViewsState(
                    listOf(ViewsTestHelper.basicSimpleLolView),
                    ViewsTestHelper.basicSimpleLolView.entitiesIds.map {
                        ViewEntity(
                            it,
                            ViewsTestHelper.basicSimpleLolView.id,
                            "alias"
                        )
                    }),
                EntitiesTestHelper.emptyEntitiesState
            )

            val spiedService = spyk(viewsEventService)
            val newName = "newName"
            val eventData = ViewToBePatchedEvent(ViewsTestHelper.id, newName, null, null, Game.LOL, false)
            val eventWithVersion = EventWithVersion(
                1L,
                Event(aggregateRoot, operationId, eventData)
            )

            val outcome = ViewsEventProcessor(eventWithVersion, spiedService).process()
            coVerify { spiedService.patchView(eq(eventData)) }

            assertEquals(
                Processed(
                    ViewPatchedEvent(ViewsTestHelper.id, newName, null, null, Game.LOL, false)
                ),
                outcome
            )

            assertView(viewsRepository, newName)
        }
    }

    private suspend fun createService(
        viewsState: ViewsState,
        entitiesState: EntitiesState,
    ): Pair<ViewsEventService, ViewsRepository> {
        val viewsRepository = ViewsInMemoryRepository()
            .withState(viewsState)
        val entitiesRepository = EntitiesInMemoryRepository()
            .withState(entitiesState)
        val wowGuildsRepository = WowGuildsInMemoryRepository()

        val wowResolver = WowEntityResolver(entitiesRepository, blizzardClient)
        val wowGuildResolver = WowGuildResolver(entitiesRepository, blizzardClient, WowSeasonInMemoryRepository())
        val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
        val wowHardcoreGuildResolver = WowHardcoreGuildResolver(entitiesRepository, blizzardClient, wowHardcoreResolver)
        val lolResolver = LolEntityResolver(entitiesRepository, riotClient)

        val lolUpdater = LolEntityUpdater(riotClient, entitiesRepository)
        val wowHardcoreGuildUpdater =
            WowHardcoreGuildUpdater(wowHardcoreGuildResolver, wowHardcoreResolver, entitiesRepository, viewsRepository)
        val wowGuildUpdater = WowGuildUpdater(wowGuildResolver, entitiesRepository, viewsRepository)

        val entitiesResolver = EntityResolverProvider(
            wowResolver = wowResolver,
            wowGuildResolver = wowGuildResolver,
            wowHardcoreResolver = wowHardcoreResolver,
            wowHardcoreGuildResolver = wowHardcoreGuildResolver,
            lolResolver = lolResolver,
            wowForeverResolver = mock(),
            wowForeverGuildResolver = mock()
        )

        val entitiesService = EntitiesService(
            entitiesRepository,
            wowGuildsRepository,
            entitiesResolver,
            wowGuildResolver,
            lolUpdater,
            wowHardcoreGuildUpdater,
            wowGuildUpdater,
            mock(),
            mock()
        )
        return Pair(ViewsEventService(viewsRepository, entitiesService), viewsRepository)
    }

    private suspend fun assertView(viewsRepository: ViewsRepository, name: String) {
        assertEquals(1, viewsRepository.state().views.size)
        val insertedView = viewsRepository.state().views.first()
        assertEquals(ViewsTestHelper.id, insertedView.id)
        assertEquals(name, insertedView.name)
        assertEquals(ViewsTestHelper.owner, insertedView.owner)
        assertEquals(listOf(), insertedView.entitiesIds)
        assertTrue(insertedView.published)
    }
}