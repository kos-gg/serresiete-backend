package com.kos.eventsourcing.subscriptions

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.ControllerError
import com.kos.common.OffsetDateTimeSerializer
import com.kos.common.Retry.retryEitherWithExponentialBackoff
import com.kos.common.RetryConfig
import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.entities.cache.LolEntityCacheService
import com.kos.entities.cache.WowEntityCacheService
import com.kos.entities.cache.WowHardcoreEntityCacheService
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
        private val syncLolEntitiesProcessorLogger =
            LoggerFactory.getLogger("eventSubscription.syncLolEntitiesProcessor")
        private val syncWowEntitiesProcessorLogger =
            LoggerFactory.getLogger("eventSubscription.syncWowEntitiesProcessor")
        private val syncWowHardcoreEntitiesProcessorLogger =
            LoggerFactory.getLogger("eventSubscription.syncWowHardcoreEntitiesProcessor")
        private val entitiesProcessorLogger =
            LoggerFactory.getLogger("eventSubscription.entitiesProcessor")

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

        suspend fun syncLolEntitiesProcessor(
            eventWithVersion: EventWithVersion,
            entitiesService: EntitiesService,
            lolEntityCacheService: LolEntityCacheService
        ): Either<ControllerError, Unit> {
            return when (eventWithVersion.event.eventData.eventType) {
                EventType.VIEW_CREATED -> {
                    val payload = eventWithVersion.event.eventData as ViewCreatedEvent
                    return when (payload.game) {
                        Game.LOL -> {
                            syncLolEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val entities = payload.entities.mapNotNull {
                                entitiesService.get(
                                    it,
                                    Game.LOL
                                )
                            }
                            lolEntityCacheService.cache(entities)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncLolEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_EDITED -> {
                    val payload = eventWithVersion.event.eventData as ViewEditedEvent
                    return when (payload.game) {
                        Game.LOL -> {
                            syncLolEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val entities = payload.entities.mapNotNull {
                                entitiesService.get(
                                    it,
                                    Game.LOL
                                )
                            }
                            lolEntityCacheService.cache(entities)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncLolEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_PATCHED -> {
                    val payload = eventWithVersion.event.eventData as ViewPatchedEvent
                    return when (payload.game) {
                        Game.LOL -> {
                            syncLolEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            payload.entities?.mapNotNull { entitiesService.get(it, Game.LOL) }?.let {
                                lolEntityCacheService.cache(it)
                            }
                            Either.Right(Unit)
                        }

                        else -> {
                            syncLolEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.REQUEST_TO_BE_SYNCED -> {
                    val payload = eventWithVersion.event.eventData as RequestToBeSynced
                    return when (payload.game) {
                        Game.LOL -> {
                            either {
                                syncLolEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")

                                val resolved =
                                    entitiesService.resolveEntities(
                                        listOf(payload.request),
                                        payload.game
                                    ).bind()

                                val inserted = entitiesService
                                    .insert(resolved.entities.map { it.first }, payload.game)
                                    .bind()

                                val entities = inserted.zip(resolved.entities.map { it.second }) +
                                        resolved.existing

                                lolEntityCacheService.cache(entities.map { it.first })
                            }
                        }

                        else -> {
                            syncLolEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                else -> {
                    syncLolEntitiesProcessorLogger.debug(
                        "skipping event v{} ({})",
                        eventWithVersion.version,
                        eventWithVersion.event.eventData.eventType
                    )
                    Either.Right(Unit)
                }
            }
        }

        suspend fun syncWowEntitiesProcessor(
            eventWithVersion: EventWithVersion,
            entitiesService: EntitiesService,
            wowEntityCacheService: WowEntityCacheService
        ): Either<ControllerError, Unit> {
            return when (eventWithVersion.event.eventData.eventType) {
                EventType.VIEW_CREATED -> {
                    val payload = eventWithVersion.event.eventData as ViewCreatedEvent
                    return when (payload.game) {
                        Game.WOW -> {
                            syncWowEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val entities = payload.entities.mapNotNull {
                                entitiesService.get(
                                    it,
                                    Game.WOW
                                )
                            }
                            wowEntityCacheService
                                .cache(entities)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_EDITED -> {
                    val payload = eventWithVersion.event.eventData as ViewEditedEvent
                    return when (payload.game) {
                        Game.WOW -> {
                            syncWowEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val entities = payload.entities.mapNotNull {
                                entitiesService.get(
                                    it,
                                    Game.WOW
                                )
                            }
                            wowEntityCacheService
                                .cache(entities)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_PATCHED -> {
                    val payload = eventWithVersion.event.eventData as ViewPatchedEvent
                    return when (payload.game) {
                        Game.WOW -> {
                            syncWowEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            payload.entities?.mapNotNull { entitiesService.get(it, Game.WOW) }?.let {
                                wowEntityCacheService
                                    .cache(it)
                            }
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.REQUEST_TO_BE_SYNCED -> {
                    val payload = eventWithVersion.event.eventData as RequestToBeSynced
                    return when (payload.game) {
                        Game.WOW -> {
                            either {
                                syncWowEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")
                                val resolved =
                                    entitiesService.resolveEntities(
                                        listOf(payload.request),
                                        payload.game
                                    ).bind()

                                val inserted = entitiesService
                                    .insert(resolved.entities.map { it.first }, payload.game)
                                    .bind()

                                val entities = inserted.zip(resolved.entities.map { it.second }) +
                                        resolved.existing
                                wowEntityCacheService.cache(entities.map { it.first })
                            }
                        }

                        else -> {
                            syncWowEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                else -> {
                    syncWowEntitiesProcessorLogger.debug(
                        "skipping event v{} ({})",
                        eventWithVersion.version,
                        eventWithVersion.event.eventData.eventType
                    )
                    Either.Right(Unit)
                }
            }
        }

        suspend fun syncWowHardcoreEntitiesProcessor(
            eventWithVersion: EventWithVersion,
            entitiesService: EntitiesService,
            wowHardcoreEntityCacheService: WowHardcoreEntityCacheService
        ): Either<ControllerError, Unit> {
            return when (eventWithVersion.event.eventData.eventType) {
                EventType.VIEW_CREATED -> {
                    val payload = eventWithVersion.event.eventData as ViewCreatedEvent
                    return when (payload.game) {
                        Game.WOW_HC -> {
                            syncWowHardcoreEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val entities = payload.entities.mapNotNull {
                                entitiesService.get(
                                    it,
                                    Game.WOW_HC
                                )
                            }
                            wowHardcoreEntityCacheService
                                .cache(entities)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowHardcoreEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_EDITED -> {
                    val payload = eventWithVersion.event.eventData as ViewEditedEvent
                    return when (payload.game) {
                        Game.WOW_HC -> {
                            syncWowHardcoreEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            val entities = payload.entities.mapNotNull {
                                entitiesService.get(
                                    it,
                                    Game.WOW_HC
                                )
                            }
                            wowHardcoreEntityCacheService
                                .cache(entities)
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowHardcoreEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.VIEW_PATCHED -> {
                    val payload = eventWithVersion.event.eventData as ViewPatchedEvent
                    return when (payload.game) {
                        Game.WOW_HC -> {
                            syncWowHardcoreEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")
                            payload.entities?.mapNotNull { entitiesService.get(it, Game.WOW_HC) }?.let {
                                wowHardcoreEntityCacheService
                                    .cache(it)
                            }
                            Either.Right(Unit)
                        }

                        else -> {
                            syncWowHardcoreEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                EventType.REQUEST_TO_BE_SYNCED -> {
                    val payload = eventWithVersion.event.eventData as RequestToBeSynced
                    return when (payload.game) {
                        Game.WOW_HC -> {
                            either {
                                syncWowHardcoreEntitiesProcessorLogger.debug("processing event v${eventWithVersion.version}")
                                val resolved =
                                    entitiesService.resolveEntities(
                                        listOf(payload.request),
                                        payload.game
                                    ).bind()

                                val inserted = entitiesService
                                    .insert(resolved.entities.map { it.first }, payload.game)
                                    .bind()

                                val entities = inserted.zip(resolved.entities.map { it.second }) +
                                        resolved.existing
                                wowHardcoreEntityCacheService.cache(entities.map { it.first })
                            }
                        }

                        else -> {
                            syncWowHardcoreEntitiesProcessorLogger.debug("skipping event v${eventWithVersion.version}")
                            Either.Right(Unit)
                        }
                    }
                }

                else -> {
                    syncLolEntitiesProcessorLogger.debug(
                        "skipping event v{} ({})",
                        eventWithVersion.version,
                        eventWithVersion.event.eventData.eventType
                    )
                    Either.Right(Unit)
                }
            }
        }

        suspend fun entitiesProcessor(
            eventWithVersion: EventWithVersion,
            entitiesService: EntitiesService
        ): Either<ControllerError, Unit> {
            return when (eventWithVersion.event.eventData.eventType) {
                EventType.VIEW_DELETED -> {
                    val payload = eventWithVersion.event.eventData as ViewDeletedEvent
                    Either.Right(payload.entities.map { it to entitiesService.getViewsFromEntity(it, payload.game) }
                        .forEach {
                            if (it.second.isEmpty()) {
                                entitiesProcessorLogger.debug("Deleting entity ${it.first}")
                                entitiesService.delete(it.first)
                            } else entitiesProcessorLogger.debug(
                                "Not deleting character {} because it's still in {}",
                                it.first,
                                it.second
                            )

                        })
                }

                else -> {
                    entitiesProcessorLogger.debug(
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

