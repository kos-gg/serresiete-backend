package com.kos.eventsourcing.subscriptions

import com.kos.eventsourcing.subscriptions.repository.SubscriptionsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class EventSubscriptionServiceTest {

    private val defaultSubscriptionState =
        mapOf(
            "testSubscription" to SubscriptionState(
                SubscriptionStatus.WAITING,
                0,
                OffsetDateTime.now(),
                null
            ),
            "testSubscription2" to SubscriptionState(
                SubscriptionStatus.RUNNING,
                0,
                OffsetDateTime.now(),
                null
            )
        )

    @Nested
    inner class BehaviourGetQueueStatus {
        @Test
        fun `i can get queue status`() {
            runBlocking {
                val eventSubscriptionService = createService(defaultSubscriptionState)
                assertEquals(defaultSubscriptionState, eventSubscriptionService.getEventSubscriptions())
            }
        }
    }

    private suspend fun createService(
        eventSubscriptionState: Map<String, SubscriptionState>
    ): EventSubscriptionService {
        val eventSubscriptionRepository =
            SubscriptionsInMemoryRepository().withState(eventSubscriptionState)

        return EventSubscriptionService(eventSubscriptionRepository)
    }

}