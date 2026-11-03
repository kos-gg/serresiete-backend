package com.kos.tasks.runners

import com.kos.tasks.TaskCompleted
import arrow.core.Either
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.eventsourcing.subscriptions.SubscriptionState
import com.kos.eventsourcing.subscriptions.SubscriptionStatus
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsInMemoryRepository
import com.kos.views.ViewsTestHelper.basicSimpleWowView
import kotlinx.coroutines.runBlocking
import java.time.OffsetDateTime
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals

class EventCleanupTaskRunnerTest {

    private val eventStore = EventStoreInMemory()
    private val subscriptionsRepo = SubscriptionsInMemoryRepository()
    private val runner = EventCleanupTaskRunner(eventStore, subscriptionsRepo)

    private val payload = ViewToBeCreatedEvent(
        UUID.randomUUID().toString(),
        basicSimpleWowView.name,
        basicSimpleWowView.published,
        listOf(),
        basicSimpleWowView.game,
        basicSimpleWowView.owner,
        basicSimpleWowView.featured,
        null
    )

    @Test
    fun `cleanup is skipped and recorded as successful when there are no subscriptions`() = runBlocking {
        eventStore.save(Event("/credentials/client1", UUID.randomUUID().toString(), payload))

        val result = runner.run(null)

        assertEquals(Either.Right(TaskCompleted()), result)
        assertEquals(1, eventStore.getEvents(null).toList().size)
    }

    @Test
    fun `cleanup uses the least advanced subscription version as the deletion bound`() =
        runBlocking {
            eventStore.save(Event("/credentials/client1", UUID.randomUUID().toString(), payload))
            eventStore.save(Event("/credentials/client1", UUID.randomUUID().toString(), payload))
            subscriptionsRepo.withState(
                mapOf(
                    "views" to SubscriptionState(SubscriptionStatus.WAITING, 2, OffsetDateTime.now()),
                    "entities" to SubscriptionState(SubscriptionStatus.WAITING, 0, OffsetDateTime.now())
                )
            )

            val result = runner.run(null)

            assertEquals(Either.Right(TaskCompleted()), result)
            assertEquals(2, eventStore.getEvents(null).toList().size)
        }
}
