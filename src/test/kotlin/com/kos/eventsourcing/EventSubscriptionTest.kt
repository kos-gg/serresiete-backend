package com.kos.eventsourcing


import arrow.core.Either
import com.kos.assertTrue
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.BlizzardDatabaseClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.NotFound
import com.kos.common.RetryConfig
import com.kos.credentials.CredentialsService
import com.kos.credentials.CredentialsTestHelper
import com.kos.credentials.repository.CredentialsInMemoryRepository
import com.kos.credentials.repository.CredentialsRepositoryState
import com.kos.datacache.DataCache
import com.kos.datacache.DataCacheService
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.WowEntity
import com.kos.entities.entitiesResolvers.LolResolver
import com.kos.entities.entitiesResolvers.WowHardcoreResolver
import com.kos.entities.entitiesResolvers.WowResolver
import com.kos.entities.entitiesUpdaters.LolUpdater
import com.kos.entities.entitiesUpdaters.WowHardcoreGuildUpdater
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.WowGuildsInMemoryRepository
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.eventsourcing.subscriptions.EventSubscription
import com.kos.eventsourcing.subscriptions.SubscriptionState
import com.kos.eventsourcing.subscriptions.SubscriptionStatus
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsInMemoryRepository
import com.kos.views.*
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsRepository
import com.kos.views.repository.ViewsState
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.assertThrows
import org.mockito.Mockito
import org.mockito.Mockito.mock
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class EventSubscriptionTest {
    private val retryConfig = RetryConfig(1, 1)
    private val raiderIoClient = Mockito.mock(RaiderIoClient::class.java)
    private val riotClient = Mockito.mock(RiotClient::class.java)
    private val blizzardClient = Mockito.mock(BlizzardClient::class.java)

    @Nested
    inner class BehaviorOfProcessPendingEvents {
        @Test
        fun `processPendingEvents throws exception if subscription is not found`() {
            runBlocking {
                val eventStore = EventStoreInMemory()
                val subscriptionsRepository = SubscriptionsInMemoryRepository()

                val subscription = EventSubscription(
                    subscriptionName = "testSubscription",
                    eventStore = eventStore,
                    subscriptionsRepository = subscriptionsRepository,
                    retryConfig = retryConfig,
                    process = { Either.Right(Unit) }
                )

                assertThrows<Exception> {
                    subscription.processPendingEvents()
                }
            }
        }

        @Test
        fun `processPendingEvents updates state to WAITING on successful processing`() {
            runBlocking {
                val eventData = ViewToBeCreatedEvent("id", "name", true, listOf(), Game.LOL, "owner", false, null)
                val event = Event("root", "id", eventData)
                val eventWithVersion = EventWithVersion(1, event)

                val eventStore = EventStoreInMemory().withState(listOf(eventWithVersion))

                val initialSubscriptionStateTime = OffsetDateTime.now()
                val subscriptionState = SubscriptionState(
                    SubscriptionStatus.WAITING,
                    version = 0,
                    time = initialSubscriptionStateTime
                )

                val subscriptionsRepository = SubscriptionsInMemoryRepository().withState(
                    mapOf(
                        "testSubscription" to subscriptionState
                    )
                )

                val subscription = EventSubscription(
                    subscriptionName = "testSubscription",
                    eventStore = eventStore,
                    subscriptionsRepository = subscriptionsRepository,
                    retryConfig = retryConfig,
                    process = { Either.Right(Unit) }
                )

                subscription.processPendingEvents()

                val finalSubscriptionState = subscriptionsRepository.getState("testSubscription")

                assertEquals(SubscriptionStatus.WAITING, finalSubscriptionState?.status)
                assertEquals(1, finalSubscriptionState?.version)
                assertTrue(initialSubscriptionStateTime.isBefore(finalSubscriptionState?.time))
            }
        }

        @Test
        fun `processPendingEvents sets state to FAILED on processing error`() {
            runBlocking {
                val eventData = ViewToBeCreatedEvent("id", "name", true, listOf(), Game.LOL, "owner", false, null)
                val event = Event("root", "id", eventData)
                val eventWithVersion = EventWithVersion(1, event)

                val eventStore = EventStoreInMemory().withState(listOf(eventWithVersion))

                val initialSubscriptionStateTime = OffsetDateTime.now()
                val subscriptionState = SubscriptionState(
                    SubscriptionStatus.WAITING,
                    version = 0,
                    time = initialSubscriptionStateTime
                )

                val subscriptionsRepository = SubscriptionsInMemoryRepository().withState(
                    mapOf(
                        "testSubscription" to subscriptionState
                    )
                )

                val subscription = EventSubscription(
                    subscriptionName = "testSubscription",
                    eventStore = eventStore,
                    subscriptionsRepository = subscriptionsRepository,
                    retryConfig = retryConfig,
                    process = { Either.Left(NotFound("Simulated error")) }
                )

                subscription.processPendingEvents()

                val finalSubscriptionState = subscriptionsRepository.getState("testSubscription")

                assertEquals(SubscriptionStatus.FAILED, finalSubscriptionState?.status)
                assertEquals(0, finalSubscriptionState?.version)
                assertTrue(initialSubscriptionStateTime.isBefore(finalSubscriptionState?.time))
            }
        }

        @Test
        fun `processPendingEvents sets state to FAILED on processing error and stops processing further events`() {
            runBlocking {
                val eventData = ViewToBeCreatedEvent("id", "name", true, listOf(), Game.LOL, "owner", false, null)
                val event = Event("root", "id", eventData)

                val events = (1L..10L).map { EventWithVersion(it, event) }

                val eventStore = EventStoreInMemory().withState(events)

                val initialSubscriptionStateTime = OffsetDateTime.now()
                val subscriptionState = SubscriptionState(
                    SubscriptionStatus.WAITING,
                    version = 0,
                    time = initialSubscriptionStateTime
                )

                val subscriptionsRepository = SubscriptionsInMemoryRepository().withState(
                    mapOf(
                        "testSubscription" to subscriptionState
                    )
                )

                val subscription = EventSubscription(
                    subscriptionName = "testSubscription",
                    eventStore = eventStore,
                    subscriptionsRepository = subscriptionsRepository,
                    retryConfig = retryConfig,
                    process = { Either.Left(NotFound("Simulated error")) }
                )

                subscription.processPendingEvents()

                val finalSubscriptionState = subscriptionsRepository.getState("testSubscription")

                assertEquals(SubscriptionStatus.FAILED, finalSubscriptionState?.status)
                assertEquals(0, finalSubscriptionState?.version)
                assertTrue(initialSubscriptionStateTime.isBefore(finalSubscriptionState?.time))
            }
        }

        @Test
        fun `processPendingEvents sets state to FAILED on processing error and stops processing further events when some events were processed`() {
            runBlocking {
                val eventData = ViewToBeCreatedEvent("id", "name", true, listOf(), Game.LOL, "owner", false, null)
                val event = Event("root", "id", eventData)

                val events = (1L..10L).map { EventWithVersion(it, event) }

                val eventStore = EventStoreInMemory().withState(events)

                val initialSubscriptionStateTime = OffsetDateTime.now()
                val subscriptionState = SubscriptionState(
                    SubscriptionStatus.WAITING,
                    version = 0,
                    time = initialSubscriptionStateTime
                )

                val subscriptionsRepository = SubscriptionsInMemoryRepository().withState(
                    mapOf(
                        "testSubscription" to subscriptionState
                    )
                )

                val subscription = EventSubscription(
                    subscriptionName = "testSubscription",
                    eventStore = eventStore,
                    subscriptionsRepository = subscriptionsRepository,
                    retryConfig = retryConfig,
                    process = {
                        if (it.version <= 5) Either.Right(Unit)
                        else Either.Left(NotFound("Simulated error"))
                    }
                )

                subscription.processPendingEvents()

                val finalSubscriptionState = subscriptionsRepository.getState("testSubscription")

                assertEquals(SubscriptionStatus.FAILED, finalSubscriptionState?.status)
                assertEquals(5, finalSubscriptionState?.version)
                assertTrue(initialSubscriptionStateTime.isBefore(finalSubscriptionState?.time))
            }
        }

        @Test
        fun `processPendingEvents retries to process the events even when in FAILED state`() {
            runBlocking {
                val eventData = ViewToBeCreatedEvent("id", "name", true, listOf(), Game.LOL, "owner", false, null)
                val event = Event("root", "id", eventData)

                val events = (1L..10L).map { EventWithVersion(it, event) }

                val eventStore = EventStoreInMemory().withState(events)

                val initialSubscriptionStateTime = OffsetDateTime.now()
                val subscriptionState = SubscriptionState(
                    SubscriptionStatus.FAILED,
                    version = 2,
                    time = initialSubscriptionStateTime
                )

                val subscriptionsRepository = SubscriptionsInMemoryRepository().withState(
                    mapOf(
                        "testSubscription" to subscriptionState
                    )
                )

                val subscription = EventSubscription(
                    subscriptionName = "testSubscription",
                    eventStore = eventStore,
                    subscriptionsRepository = subscriptionsRepository,
                    retryConfig = retryConfig,
                    process = { Either.Right(Unit) }
                )

                subscription.processPendingEvents()

                val finalSubscriptionState = subscriptionsRepository.getState("testSubscription")

                assertEquals(SubscriptionStatus.WAITING, finalSubscriptionState?.status)
                assertEquals(10, finalSubscriptionState?.version)
                assertTrue(initialSubscriptionStateTime.isBefore(finalSubscriptionState?.time))
            }
        }
    }

    @Nested
    inner class BehaviorOfViewsProcessor {
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

                EventSubscription.viewsProcessor(eventWithVersion, spiedService)
                    .onLeft { fail("Expected success") }
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

                EventSubscription.viewsProcessor(eventWithVersion, spiedService)
                    .onLeft { fail("Expected success") }
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

                EventSubscription.viewsProcessor(eventWithVersion, spiedService)
                    .onLeft { fail("Expected success") }
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

            val wowResolver = WowResolver(entitiesRepository, raiderIoClient)
            val wowHardcoreResolver = WowHardcoreResolver(entitiesRepository, blizzardClient)
            val lolResolver = LolResolver(entitiesRepository, riotClient)

            val lolUpdater = LolUpdater(riotClient, entitiesRepository)
            val wowHardcoreGuildUpdater =
                WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository)

            val entitiesResolver = mapOf(
                Game.WOW to wowResolver,
                Game.WOW_HC to wowHardcoreResolver,
                Game.LOL to lolResolver
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

    @Nested
    inner class BehaviorOfCharactersProcessor {
        private val aggregateRoot = "/credentials/owner"

        @Test
        fun `charactersProcessor deletes characters that are not present on any view when a view has been deleted`() {
            runBlocking {
                val charactersFromDeletedView: List<Long> = listOf(1, 2, 3)
                val expectedRemainingCharacters: List<Long> = listOf(1, 4, 5)
                val wowHardcoreRemainingView =
                    SimpleView("1", "wowHcView", "owner", true, expectedRemainingCharacters, Game.WOW_HC, true)
                val viewsRepository = ViewsInMemoryRepository().withState(
                    ViewsState(
                        listOf(wowHardcoreRemainingView),
                        wowHardcoreRemainingView.entitiesIds.map {
                            ViewEntity(
                                it,
                                wowHardcoreRemainingView.id,
                                "alias"
                            )
                        })
                )
                val wowHardcoreCharacters =
                    (1..5).map { WowEntity(it.toLong(), it.toString(), it.toString(), it.toString(), it.toLong()) }
                val entitiesRepository = EntitiesInMemoryRepository(viewsRepository = viewsRepository).withState(
                    EntitiesState(
                        wowEntities = listOf(),
                        lolEntities = listOf(),
                        wowHardcoreEntities = wowHardcoreCharacters
                    )
                )

                val wowGuildsRepository = WowGuildsInMemoryRepository()

                val wowResolver = WowResolver(entitiesRepository, raiderIoClient)
                val wowHardcoreResolver = WowHardcoreResolver(entitiesRepository, blizzardClient)
                val lolResolver = LolResolver(entitiesRepository, riotClient)

                val lolUpdater = LolUpdater(riotClient, entitiesRepository)
                val wowHardcoreGuildUpdater =
                    WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository)

                val entitiesResolver = mapOf(
                    Game.WOW to wowResolver,
                    Game.WOW_HC to wowHardcoreResolver,
                    Game.LOL to lolResolver
                )


                val service = EntitiesService(
                    entitiesRepository,
                    wowGuildsRepository,
                    entitiesResolver,
                    lolUpdater,
                    wowHardcoreGuildUpdater
                )

                val eventData =
                    ViewDeletedEvent(
                        "deleted",
                        "name",
                        "owner",
                        charactersFromDeletedView,
                        true,
                        Game.WOW_HC,
                        false
                    )

                val eventWithVersion = EventWithVersion(
                    1L,
                    Event(aggregateRoot, ViewsTestHelper.id, eventData)
                )

                EventSubscription.entitiesProcessor(eventWithVersion, service)
                assertEquals(
                    expectedRemainingCharacters,
                    entitiesRepository.state().wowHardcoreEntities.map { it.id })

            }
        }
    }

}