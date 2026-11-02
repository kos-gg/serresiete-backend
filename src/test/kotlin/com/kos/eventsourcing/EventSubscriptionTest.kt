package com.kos.eventsourcing

import com.kos.assertTrue
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventType
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.OperationFailedEvent
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.eventsourcing.subscriptions.EventProcessOutcome
import com.kos.eventsourcing.subscriptions.EventSubscription
import com.kos.eventsourcing.subscriptions.SubscriptionState
import com.kos.eventsourcing.subscriptions.SubscriptionStatus
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsInMemoryRepository
import com.kos.views.Game
import io.mockk.coEvery
import io.mockk.spyk
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.assertThrows
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals

class EventSubscriptionTest {

    @Test
    fun `processPendingEvents throws exception if subscription is not found`() {
        runBlocking {
            val eventStore = EventStoreInMemory()
            val subscriptionsRepository = SubscriptionsInMemoryRepository()

            val subscription = EventSubscription(
                subscriptionName = "testSubscription",
                eventStore = eventStore,
                subscriptionsRepository = subscriptionsRepository,
                process = { EventProcessOutcome.Processed }
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
                process = { EventProcessOutcome.Processed }
            )

            subscription.processPendingEvents()

            val finalSubscriptionState = subscriptionsRepository.getState("testSubscription")

            assertEquals(SubscriptionStatus.WAITING, finalSubscriptionState?.status)
            assertEquals(1, finalSubscriptionState?.version)
            assertTrue(initialSubscriptionStateTime.isBefore(finalSubscriptionState?.time))
        }
    }

    @Test
    fun `processPendingEvents skips the failing event and records the error, but still advances`() {
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
                process = { error("Simulated error") }
            )

            subscription.processPendingEvents()

            val finalSubscriptionState = subscriptionsRepository.getState("testSubscription")

            assertEquals(SubscriptionStatus.WAITING, finalSubscriptionState?.status)
            assertEquals(1, finalSubscriptionState?.version)
            assertTrue(finalSubscriptionState?.lastError?.contains("Simulated error") == true)
            assertTrue(initialSubscriptionStateTime.isBefore(finalSubscriptionState?.time))
        }
    }

    @Test
    fun `processPendingEvents keeps processing later events after a failing one`() {
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
                process = { error("Simulated error") }
            )

            subscription.processPendingEvents()

            val finalSubscriptionState = subscriptionsRepository.getState("testSubscription")

            assertEquals(SubscriptionStatus.WAITING, finalSubscriptionState?.status)
            assertEquals(10, finalSubscriptionState?.version)
            assertTrue(finalSubscriptionState?.lastError?.contains("Simulated error") == true)
            assertTrue(initialSubscriptionStateTime.isBefore(finalSubscriptionState?.time))
        }
    }

    @Test
    fun `processPendingEvents skips a failing event in the middle and still processes the rest`() {
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
                process = {
                    if (it.version == 5L) error("Simulated error")
                    else EventProcessOutcome.Processed
                }
            )

            subscription.processPendingEvents()

            val finalSubscriptionState = subscriptionsRepository.getState("testSubscription")

            assertEquals(SubscriptionStatus.WAITING, finalSubscriptionState?.status)
            assertEquals(10, finalSubscriptionState?.version)
            assertTrue(finalSubscriptionState?.lastError?.contains("Simulated error") == true)
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
                process = { EventProcessOutcome.Processed }
            )

            subscription.processPendingEvents()

            val finalSubscriptionState = subscriptionsRepository.getState("testSubscription")

            assertEquals(SubscriptionStatus.WAITING, finalSubscriptionState?.status)
            assertEquals(10, finalSubscriptionState?.version)
            assertTrue(initialSubscriptionStateTime.isBefore(finalSubscriptionState?.time))
        }
    }

    @Test
    fun `processPendingEvents records an OperationFailedEvent in the event store when processing fails`() {
        runBlocking {
            val eventData = ViewToBeCreatedEvent("id", "name", true, listOf(), Game.LOL, "owner", false, null)
            val event = Event("root", "id", eventData)
            val eventWithVersion = EventWithVersion(1, event)

            val eventStore = EventStoreInMemory().withState(listOf(eventWithVersion))

            val subscriptionState =
                SubscriptionState(SubscriptionStatus.WAITING, version = 0, time = OffsetDateTime.now())
            val subscriptionsRepository = SubscriptionsInMemoryRepository().withState(
                mapOf("testSubscription" to subscriptionState)
            )

            val subscription = EventSubscription(
                subscriptionName = "testSubscription",
                eventStore = eventStore,
                subscriptionsRepository = subscriptionsRepository,
                process = { error("Simulated error") }
            )

            subscription.processPendingEvents()

            val recordedFailures = eventStore.getEventsByOperationId("id")
                .filter { it.event.eventData is OperationFailedEvent }

            assertEquals(1, recordedFailures.size)
            val failure = recordedFailures.first()
            assertEquals("root", failure.event.aggregateRoot)
            val failed = failure.event.eventData as OperationFailedEvent
            assertEquals(EventType.VIEW_TO_BE_CREATED, failed.error?.eventType)
            assertEquals(1, failed.error?.version)
            assertEquals(eventData, failed.error?.payload)
            assertTrue(failed.error?.stackTrace?.startsWith("java.lang.IllegalStateException: Simulated error") == true)
            assertTrue(failed.error?.stackTrace?.contains("\tat ") == true)
        }
    }

    @Test
    fun `processPendingEvents does not advance past a failing event whose failure could not be stored`() {
        runBlocking {
            val eventData = ViewToBeCreatedEvent("id", "name", true, listOf(), Game.LOL, "owner", false, null)
            val eventWithVersion = EventWithVersion(1, Event("root", "id", eventData))

            val eventStore = spyk(EventStoreInMemory().withState(listOf(eventWithVersion)))
            coEvery { eventStore.saveFailedEvent(any(), any()) } throws IllegalStateException("database is down")

            val subscriptionState =
                SubscriptionState(SubscriptionStatus.WAITING, version = 0, time = OffsetDateTime.now())
            val subscriptionsRepository = SubscriptionsInMemoryRepository().withState(
                mapOf("testSubscription" to subscriptionState)
            )

            val subscription = EventSubscription(
                subscriptionName = "testSubscription",
                eventStore = eventStore,
                subscriptionsRepository = subscriptionsRepository,
                process = { error("Simulated error") }
            )

            assertThrows<IllegalStateException> {
                subscription.processPendingEvents()
            }

            assertEquals(subscriptionState, subscriptionsRepository.getState("testSubscription"))
        }
    }

    @Test
    fun `processPendingEvents does not swallow CancellationException`() {
        runBlocking {
            val eventData = ViewToBeCreatedEvent("id", "name", true, listOf(), Game.LOL, "owner", false, null)
            val event = Event("root", "id", eventData)
            val eventWithVersion = EventWithVersion(1, event)

            val eventStore = EventStoreInMemory().withState(listOf(eventWithVersion))

            val subscriptionState =
                SubscriptionState(SubscriptionStatus.WAITING, version = 0, time = OffsetDateTime.now())
            val subscriptionsRepository = SubscriptionsInMemoryRepository().withState(
                mapOf("testSubscription" to subscriptionState)
            )

            val subscription = EventSubscription(
                subscriptionName = "testSubscription",
                eventStore = eventStore,
                subscriptionsRepository = subscriptionsRepository,
                process = { throw CancellationException("job was cancelled") }
            )

            assertThrows<CancellationException> {
                subscription.processPendingEvents()
            }

            val recordedFailures = eventStore.getEventsByOperationId("id")
                .filter { it.event.eventData is OperationFailedEvent }
            assertEquals(0, recordedFailures.size)

            val finalSubscriptionState = subscriptionsRepository.getState("testSubscription")
            assertEquals(0, finalSubscriptionState?.version)
        }
    }
}

