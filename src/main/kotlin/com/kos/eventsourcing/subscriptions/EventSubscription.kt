package com.kos.eventsourcing.subscriptions

import arrow.core.Either
import com.kos.common.error.ControllerError
import com.kos.common.OffsetDateTimeSerializer
import com.kos.common.Retry.retryEitherWithExponentialBackoff
import com.kos.common.RetryConfig
import com.kos.common.WithLogger
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
    private val retryConfig: RetryConfig,
    private val process: suspend (EventWithVersion) -> Either<ControllerError, Unit>,
) : WithLogger("event-subscription-$subscriptionName") {

    init {
        logger.info("started subscription")
    }

    suspend fun processPendingEvents(): Unit {
        val initialState: SubscriptionState =
            subscriptionsRepository.getState(subscriptionName)
                ?: throw Exception("Not found subscription $subscriptionName")
        val hasSucceededWithVersion =
            eventStore.getEvents(initialState.version)
                .fold(Pair(true, initialState.version)) { (shouldKeepGoing, version), event ->
                    if (shouldKeepGoing) {
                        try {
                            retryEitherWithExponentialBackoff(retryConfig) { process(event) }
                                .onLeft { throw Exception(it.toString()) }
                            subscriptionsRepository.setState(
                                subscriptionName,
                                SubscriptionState(SubscriptionStatus.RUNNING, event.version, OffsetDateTime.now())
                            )
                            Pair(true, event.version)
                        } catch (e: Exception) {
                            subscriptionsRepository.setState(
                                subscriptionName,
                                SubscriptionState(
                                    SubscriptionStatus.FAILED,
                                    event.version - 1,
                                    OffsetDateTime.now(),
                                    e.message
                                )
                            )
                            logger.error("processing event ${event.version} has failed because ${e.message}")
                            logger.debug(e.stackTraceToString())
                            Pair(false, event.version)
                        }
                    } else Pair(false, version)
                }
        if (hasSucceededWithVersion.first) {
            subscriptionsRepository.setState(
                subscriptionName,
                SubscriptionState(SubscriptionStatus.WAITING, hasSucceededWithVersion.second, OffsetDateTime.now())
            )
        }
    }
}

