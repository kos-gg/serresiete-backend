package com.kos.eventsourcing.subscriptions.sync

import com.kos.assertTrue
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.credentials.CredentialsService
import com.kos.credentials.CredentialsTestHelper
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.credentials.repository.CredentialsRepositoryState
import com.kos.datacache.DataCache
import com.kos.datacache.DataCacheService
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.EntityResolverProvider
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.Game
import com.kos.views.ViewEntity
import com.kos.views.ViewsService
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

class ViewsSyncProcessorTest {
    private val raiderIoClient = Mockito.mock(RaiderIoClient::class.java)
    private val riotClient = Mockito.mock(RiotClient::class.java)
    private val blizzardClient = Mockito.mock(BlizzardClient::class.java)

    private val aggregateRoot = "/credentials/owner"

    @Test
    fun `viewsProcessor calls createView on VIEW_TO_BE_CREATED event, creates a view and stores an event`() {
        runBlocking {
            val (eventStore, viewsService, viewsRepository) = createService(
                ViewsState(listOf(), listOf()),
                EntitiesTestHelper.emptyEntitiesState,
                listOf(),
                CredentialsTestHelper.emptyCredentialsInitialState
            )

            val spiedService = spyk(viewsService)

            val eventData =
                ViewToBeCreatedEvent(ViewsTestHelper.id, "name", true, listOf(), Game.LOL, "owner", false, null)
            val eventWithVersion = EventWithVersion(
                1L,
                Event(aggregateRoot, ViewsTestHelper.id, eventData)
            )

            ViewsEventProcessor(eventWithVersion, spiedService).process()
                .onLeft { kotlin.test.fail("Expected success") }
                .onRight {
                    coVerify {
                        spiedService.createView(
                            eq(ViewsTestHelper.id),
                            eq(aggregateRoot),
                            eq(eventData)
                        )
                    }
                }


            assertEventStoredCorrectly(
                eventStore,
                ViewCreatedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    ViewsTestHelper.owner, listOf(), true, Game.LOL, false, null
                )
            )

            assertView(viewsRepository, ViewsTestHelper.name)
        }
    }

    @Test
    fun `viewsProcessor calls edit view on VIEW_TO_BE_EDITED event, edits a view and stores an event`() {
        runBlocking {
            val (eventStore, viewsService, viewsRepository) = createService(
                ViewsState(
                    listOf(ViewsTestHelper.basicSimpleLolView),
                    ViewsTestHelper.basicSimpleLolView.entitiesIds.map {
                        ViewEntity(
                            it,
                            ViewsTestHelper.basicSimpleLolView.id,
                            "alias"
                        )
                    }),
                EntitiesTestHelper.emptyEntitiesState,
                listOf(),
                CredentialsTestHelper.emptyCredentialsInitialState
            )

            val spiedService = spyk(viewsService)

            val newName = "new-name"
            val eventData = ViewToBeEditedEvent(ViewsTestHelper.id, newName, true, listOf(), Game.LOL, false)
            val eventWithVersion = EventWithVersion(
                1L,
                Event(aggregateRoot, ViewsTestHelper.id, eventData)
            )

            ViewsEventProcessor(eventWithVersion, spiedService).process()
                .onLeft { kotlin.test.fail("Expected success") }
                .onRight {
                    coVerify {
                        spiedService.editView(
                            eq(ViewsTestHelper.id),
                            eq(aggregateRoot),
                            eq(eventData)
                        )
                    }
                }

            assertEventStoredCorrectly(
                eventStore,
                ViewEditedEvent(ViewsTestHelper.id, newName, listOf(), true, Game.LOL, false)
            )

            assertView(viewsRepository, newName)
        }
    }

    @Test
    fun `viewsProcessor calls patch view on VIEW_TO_BE_PATCHED event, patches a view and stores an event`() {
        runBlocking {
            val (eventStore, viewsService, viewsRepository) = createService(
                ViewsState(
                    listOf(ViewsTestHelper.basicSimpleLolView),
                    ViewsTestHelper.basicSimpleLolView.entitiesIds.map {
                        ViewEntity(
                            it,
                            ViewsTestHelper.basicSimpleLolView.id,
                            "alias"
                        )
                    }),
                EntitiesTestHelper.emptyEntitiesState,
                listOf(),
                CredentialsTestHelper.emptyCredentialsInitialState,
            )

            val spiedService = spyk(viewsService)
            val newName = "newName"
            val eventData = ViewToBePatchedEvent(ViewsTestHelper.id, newName, null, null, Game.LOL, false)
            val eventWithVersion = EventWithVersion(
                1L,
                Event(aggregateRoot, ViewsTestHelper.id, eventData)
            )

            ViewsEventProcessor(eventWithVersion, spiedService).process()
                .onLeft { kotlin.test.fail("Expected success") }
                .onRight {
                    coVerify {
                        spiedService.patchView(
                            eq(ViewsTestHelper.id),
                            eq(aggregateRoot),
                            eq(eventData)
                        )
                    }
                }


            assertEventStoredCorrectly(
                eventStore,
                ViewPatchedEvent(ViewsTestHelper.id, newName, null, null, Game.LOL, false)
            )

            assertView(viewsRepository, newName)
        }
    }

    private suspend fun createService(
        viewsState: ViewsState,
        entitiesState: EntitiesState,
        dataCacheState: List<DataCache>,
        credentialState: CredentialsRepositoryState,
    ): Triple<EventStore, ViewsService, ViewsRepository> {
        val viewsRepository = ViewsInMemoryRepository()
            .withState(viewsState)
        val entitiesRepository = EntitiesInMemoryRepository()
            .withState(entitiesState)
        val dataCacheRepository = DataCacheInMemoryRepository()
            .withState(dataCacheState)
        val credentialsRepository = CredentialsInMemoryRepository()
            .withState(credentialState)
        val eventStore = EventStoreInMemory()
        val wowGuildsRepository = WowGuildsInMemoryRepository()

        val wowResolver = WowEntityResolver(entitiesRepository, raiderIoClient)
        val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
        val lolResolver = LolEntityResolver(entitiesRepository, riotClient)

        val lolUpdater = LolEntityUpdater(riotClient, entitiesRepository)
        val wowHardcoreGuildUpdater =
            WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository)

        val entitiesResolver = EntityResolverProvider(
            listOf(
                wowResolver,
                wowHardcoreResolver,
                lolResolver
            )
        )

        val credentialsService = CredentialsService(credentialsRepository)
        val entitiesService = EntitiesService(
            entitiesRepository,
            wowGuildsRepository,
            entitiesResolver,
            lolUpdater,
            wowHardcoreGuildUpdater
        )
        val dataCacheService =
            DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                eventStore
            )
        val service =
            ViewsService(
                viewsRepository,
                entitiesService,
                dataCacheService,
                credentialsService,
                eventStore
            )

        return Triple(eventStore, service, viewsRepository)
    }

    private suspend fun assertEventStoredCorrectly(eventStore: EventStore, eventData: EventData) {
        val events = eventStore.getEvents(null).toList()

        val expectedEvent = Event(
            aggregateRoot,
            ViewsTestHelper.id,
            eventData
        )

        assertEquals(1, events.size)
        assertEquals(EventWithVersion(1, expectedEvent), events.first())
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