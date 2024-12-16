package com.kos.eventsourcing.subscriptions

import com.kos.activities.Activities
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import java.time.OffsetDateTime
import kotlin.test.Test

class EventSubscriptionControllerTest {
    private val eventSubscriptionRepository = SubscriptionsInMemoryRepository()

    private val defaultSubscriptionState =
        mapOf(
            "testSubscription" to SubscriptionState(
                SubscriptionStatus.WAITING,
                0,
                OffsetDateTime.now(),
                null
            )
        )

    private suspend fun createController(
        eventSubscriptionState: Map<String, SubscriptionState>
    ): EventSubscriptionController {
        val eventSubscriptionRepositoryWithState = eventSubscriptionRepository.withState(eventSubscriptionState)

        val eventSubscriptionService = EventSubscriptionService(eventSubscriptionRepositoryWithState)

        return EventSubscriptionController(eventSubscriptionService)
    }

    @Test
    fun `i can get queue status if perms are given`() {
        runBlocking {
            val eventSubscriptionController = createController(defaultSubscriptionState)
            assertEquals(
                defaultSubscriptionState,
                eventSubscriptionController.getEventSubscriptions("owner", setOf(Activities.getQueueStatus)).getOrNull()
            )
        }
    }
}