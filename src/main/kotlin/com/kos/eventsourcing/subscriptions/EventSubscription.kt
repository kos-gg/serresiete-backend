package com.kos.eventsourcing.subscriptions

import arrow.core.raise.catch
import com.kos.common.OffsetDateTimeSerializer
import com.kos.common.WithLogger
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventData
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsRepository
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

enum class SubscriptionStatus {
    WAITING,
    RUNNING,
    FAILED
}

sealed interface EventProcessOutcome {
    data class Processed(val emitted: EventData? = null) : EventProcessOutcome {
        override fun toString() = "processed" + emitted?.let { ", emitted ${it.eventType}" }.orEmpty()
    }

    data object Skipped : EventProcessOutcome {
        override fun toString() = "skipped"
    }
}

@Serializable
data class SubscriptionState(
    val status: SubscriptionStatus,
    val version: Long,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val time: OffsetDateTime,
    val lastError: String? = null
)

class EventSubscription(
    private val subscriptionName: String,
    private val eventStore: EventStore,
    private val subscriptionsRepository: SubscriptionsRepository,
    private val process: suspend (EventWithVersion) -> EventProcessOutcome,
) : WithLogger("event-subscription-$subscriptionName") {

    init {
        logger.info("started subscription")
    }

    suspend fun processPendingEvents(): Unit {
        val initialState: SubscriptionState =
            subscriptionsRepository.getState(subscriptionName)
                ?: throw Exception("Not found subscription $subscriptionName")

        val (finalVersion, lastError) = eventStore.getEvents(initialState.version)
            .fold(Pair(initialState.version, initialState.lastError)) { (_, currentLastError), event ->
                val error: String? = catch({ handle(event); null }) { saveFailedEvent(event, it) }

                subscriptionsRepository.setState(
                    subscriptionName,
                    SubscriptionState(
                        SubscriptionStatus.RUNNING,
                        event.version,
                        OffsetDateTime.now(),
                        error ?: currentLastError
                    )
                )
                Pair(event.version, error ?: currentLastError)
            }

        subscriptionsRepository.setState(
            subscriptionName,
            SubscriptionState(SubscriptionStatus.WAITING, finalVersion, OffsetDateTime.now(), lastError)
        )
    }

    private suspend fun handle(event: EventWithVersion) {
        val outcome = process(event)
        if (outcome is EventProcessOutcome.Processed) outcome.emitted?.let {
            eventStore.save(Event(event.event.aggregateRoot, event.event.operationId, it))
        }
        logger.info("Event v{} ({}) {}", event.version, event.event.eventData.eventType, outcome)
    }

    private suspend fun saveFailedEvent(event: EventWithVersion, throwable: Throwable): String {
        logger.error("processing event ${event.version} has failed, skipping it", throwable)
        eventStore.saveFailedEvent(event, throwable.stackTraceToString())
        return throwable.message ?: throwable.javaClass.simpleName
    }
}

