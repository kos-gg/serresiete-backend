package com.kos.eventsourcing.subscriptions

import arrow.core.Either
import arrow.core.raise.either
import com.kos.characters.CharactersService
import com.kos.common.ControllerError
import com.kos.common.OffsetDateTimeSerializer
import com.kos.common.Retry.retryEitherWithExponentialBackoff
import com.kos.common.RetryConfig
import com.kos.common.WithLogger
import com.kos.datacache.DataCacheService
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.subscriptions.repository.SubscriptionsRepository
import com.kos.views.Game
import com.kos.views.ViewsService
import kotlinx.serialization.Serializable
import org.slf4j.LoggerFactory
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

    companion object {
        private val viewsProcessorLogger = LoggerFactory.getLogger("eventSubscription.viewsProcessor")
        private val syncLolCharactersProcessorLogger =
            LoggerFactory.getLogger("eventSubscription.syncLolCharactersProcessor")
        private val syncWowCharactersProcessorLogger =
            LoggerFactory.getLogger("eventSubscription.syncWowCharactersProcessor")
        private val syncWowHardcoreCharactersProcessorLogger =
            LoggerFactory.getLogger("eventSubscription.syncWowHardcoreCharactersProcessor")
        private val charactersProcessorLogger =
            LoggerFactory.getLogger("eventSubscription.charactersProcessor")

        suspend fun viewsProcessor(
            eventWithVersion: EventWithVersion,
            viewsService: ViewsService,
        ): Either<ControllerError, Unit> {
            return when (eventWithVersion.event.eventData.eventType) {
                EventType.VIEW_TO_BE_CREATED -> {
                    either {
                        viewsProcessorLogger.debug("processing event v${eventWithVersion.version}")
                        val payload = eventWithVersion.event.eventData as ViewToBeCreatedEvent
                        val aggregateRoot = eventWithVersion.event.aggregateRoot
                        val operationId = eventWithVersion.event.operationId
                        viewsService.createView(operationId, aggregateRoot, payload).bind()
                    }
                }

                EventType.VIEW_TO_BE_EDITED -> {
                    either {
                        viewsProcessorLogger.debug("processing event v${eventWithVersion.version}")
                        val payload = eventWithVersion.event.eventData as ViewToBeEditedEvent
                        val aggregateRoot = eventWithVersion.event.aggregateRoot
                        val operationId = eventWithVersion.event.operationId
                        viewsService.editView(operationId, aggregateRoot, payload).bind()
                    }
                }

                EventType.VIEW_TO_BE_PATCHED -> {
                    either {
                        viewsProcessorLogger.debug("processing event v${eventWithVersion.version}")
                        val payload = eventWithVersion.event.eventData as ViewToBePatchedEvent
                        val aggregateRoot = eventWithVersion.event.aggregateRoot
                        val operationId = eventWithVersion.event.operationId
                        viewsService.patchView(operationId, aggregateRoot, payload).bind()
                    }
                }

                else -> {
                    viewsProcessorLogger.debug(
                        "skipping event v{} ({})",
                        eventWithVersion.version,
                        eventWithVersion.event.eventData.eventType
                    )
                    Either.Right(Unit)
                }
            }
        }

        suspend fun characterProcessor() {

        }

        suspend fun syncLolCharactersProcessor(
            eventWithVersion: EventWithVersion,
            charactersService: CharactersService,
            dataCacheService: DataCacheService
        ): Either<ControllerError, Unit> {
            return when (eventWithVersion.event.eventData.eventType) {
                EventType.VIEW_CREATED -> {
                    val payload = eventWithVersion.event.eventData as ViewCreatedEvent
                    return when (payload.game) {
                        Game.LOL -> {
                            syncLolCharactersProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val viewCharacters = payload.characters.mapNotNull {
                                charactersService.get(
                                    it,
                                    Game.LOL
                                )
                            }
                            dataCacheService.cache(viewCharacters, payload.game)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncLolCharactersProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_EDITED -> {
                    val payload = eventWithVersion.event.eventData as ViewEditedEvent
                    return when (payload.game) {
                        Game.LOL -> {
                            syncLolCharactersProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val viewCharacters = payload.characters.mapNotNull {
                                charactersService.get(
                                    it,
                                    Game.LOL
                                )
                            }
                            dataCacheService.cache(viewCharacters, payload.game)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncLolCharactersProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_PATCHED -> {
                    val payload = eventWithVersion.event.eventData as ViewPatchedEvent
                    return when (payload.game) {
                        Game.LOL -> {
                            syncLolCharactersProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            payload.characters?.mapNotNull { charactersService.get(it, Game.LOL) }?.let {
                                dataCacheService.cache(it, payload.game)
                            }
                            Either.Right(Unit)
                        }

                        else -> {
                            syncLolCharactersProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                else -> {
                    syncLolCharactersProcessorLogger.debug(
                        "skipping event v{} ({})",
                        eventWithVersion.version,
                        eventWithVersion.event.eventData.eventType
                    )
                    Either.Right(Unit)
                }
            }
        }

        suspend fun syncWowCharactersProcessor(
            eventWithVersion: EventWithVersion,
            charactersService: CharactersService,
            dataCacheService: DataCacheService
        ): Either<ControllerError, Unit> {
            return when (eventWithVersion.event.eventData.eventType) {
                EventType.VIEW_CREATED -> {
                    val payload = eventWithVersion.event.eventData as ViewCreatedEvent
                    return when (payload.game) {
                        Game.WOW -> {
                            syncWowCharactersProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val viewCharacters = payload.characters.mapNotNull {
                                charactersService.get(
                                    it,
                                    Game.WOW
                                )
                            }
                            dataCacheService.cache(viewCharacters, payload.game)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowCharactersProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_EDITED -> {
                    val payload = eventWithVersion.event.eventData as ViewEditedEvent
                    return when (payload.game) {
                        Game.WOW -> {
                            syncWowCharactersProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val viewCharacters = payload.characters.mapNotNull {
                                charactersService.get(
                                    it,
                                    Game.WOW
                                )
                            }
                            dataCacheService.cache(viewCharacters, payload.game)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowCharactersProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_PATCHED -> {
                    val payload = eventWithVersion.event.eventData as ViewPatchedEvent
                    return when (payload.game) {
                        Game.WOW -> {
                            syncWowCharactersProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            payload.characters?.mapNotNull { charactersService.get(it, Game.WOW) }?.let {
                                dataCacheService.cache(it, payload.game)
                            }
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowCharactersProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                else -> {
                    syncWowCharactersProcessorLogger.debug(
                        "skipping event v{} ({})",
                        eventWithVersion.version,
                        eventWithVersion.event.eventData.eventType
                    )
                    Either.Right(Unit)
                }
            }
        }

        suspend fun syncWowHardcoreCharactersProcessor(
            eventWithVersion: EventWithVersion,
            charactersService: CharactersService,
            dataCacheService: DataCacheService
        ): Either<ControllerError, Unit> {
            return when (eventWithVersion.event.eventData.eventType) {
                EventType.VIEW_CREATED -> {
                    val payload = eventWithVersion.event.eventData as ViewCreatedEvent
                    return when (payload.game) {
                        Game.WOW_HC -> {
                            syncWowHardcoreCharactersProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val viewCharacters = payload.characters.mapNotNull {
                                charactersService.get(
                                    it,
                                    Game.WOW_HC
                                )
                            }
                            dataCacheService.cache(viewCharacters, payload.game)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowHardcoreCharactersProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_EDITED -> {
                    val payload = eventWithVersion.event.eventData as ViewEditedEvent
                    return when (payload.game) {
                        Game.WOW_HC -> {
                            syncWowHardcoreCharactersProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val viewCharacters = payload.characters.mapNotNull {
                                charactersService.get(
                                    it,
                                    Game.WOW_HC
                                )
                            }
                            dataCacheService.cache(viewCharacters, payload.game)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowHardcoreCharactersProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_PATCHED -> {
                    val payload = eventWithVersion.event.eventData as ViewPatchedEvent
                    return when (payload.game) {
                        Game.WOW_HC -> {
                            syncWowHardcoreCharactersProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            payload.characters?.mapNotNull { charactersService.get(it, Game.WOW_HC) }?.let {
                                dataCacheService.cache(it, payload.game)
                            }
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowHardcoreCharactersProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                else -> {
                    syncLolCharactersProcessorLogger.debug(
                        "skipping event v{} ({})",
                        eventWithVersion.version,
                        eventWithVersion.event.eventData.eventType
                    )
                    Either.Right(Unit)
                }
            }
        }

        suspend fun charactersProcessor(
            eventWithVersion: EventWithVersion,
            charactersService: CharactersService
        ): Either<ControllerError, Unit> {
            return when (eventWithVersion.event.eventData.eventType) {
                EventType.VIEW_DELETED -> {
                    val payload = eventWithVersion.event.eventData as ViewDeletedEvent
                    Either.Right(payload.characters.map { it to charactersService.getViewsFromCharacter(it, payload.game) }.forEach {
                        if (it.second.isEmpty()) {
                            charactersProcessorLogger.debug("Deleting character ${it.first}")
                            charactersService.delete(it.first, payload.game)
                        }
                        else charactersProcessorLogger.debug(
                            "Not deleting character {} because it's still in {}",
                            it.first,
                            it.second
                        )

                    })
                }

                else -> {
                    charactersProcessorLogger.debug(
                        "skipping event v{} ({})",
                        eventWithVersion.version,
                        eventWithVersion.event.eventData.eventType
                    )
                    Either.Right(Unit)
                }
            }
        }
    }
}

