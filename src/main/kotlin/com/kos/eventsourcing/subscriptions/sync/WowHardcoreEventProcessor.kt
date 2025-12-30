package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.ControllerError
import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.eventsourcing.events.*
import com.kos.sources.wowhc.WowHardcoreEntitySynchronizer
import com.kos.views.Game


class WowHardcoreEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val entitiesService: EntitiesService,
    private val wowHardcoreEntityCacheService: WowHardcoreEntitySynchronizer
) : EventProcessor, WithLogger("eventSubscription.syncWowHardcoreEntitiesProcessor") {

    override suspend fun process(): Either<ControllerError, Unit> {
        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_CREATED -> {
                val payload = eventWithVersion.event.eventData as ViewCreatedEvent
                return when (payload.game) {
                    Game.WOW_HC -> {
                        logger.debug("processing event v${eventWithVersion.version}")
                        val entities = payload.entities.mapNotNull {
                            entitiesService.get(
                                it,
                                Game.WOW_HC
                            )
                        }
                        wowHardcoreEntityCacheService
                            .synchronize(entities)
                        Either.Right(Unit)
                    }

                    else -> {
                        logger.debug("skipping event v${eventWithVersion.version}")
                        Either.Right(Unit)
                    }
                }
            }

            EventType.VIEW_EDITED -> {
                val payload = eventWithVersion.event.eventData as ViewEditedEvent
                return when (payload.game) {
                    Game.WOW_HC -> {
                        logger.debug("processing event v${eventWithVersion.version}")
                        val entities = payload.entities.mapNotNull {
                            entitiesService.get(
                                it,
                                Game.WOW_HC
                            )
                        }
                        wowHardcoreEntityCacheService
                            .synchronize(entities)
                        Either.Right(Unit)
                    }

                    else -> {
                        logger.debug("skipping event v${eventWithVersion.version}")
                        Either.Right(Unit)
                    }
                }
            }

            EventType.VIEW_PATCHED -> {
                val payload = eventWithVersion.event.eventData as ViewPatchedEvent
                return when (payload.game) {
                    Game.WOW_HC -> {
                        logger.debug("processing event v${eventWithVersion.version}")
                        payload.entities?.mapNotNull { entitiesService.get(it, Game.WOW_HC) }?.let {
                            wowHardcoreEntityCacheService
                                .synchronize(it)
                        }
                        Either.Right(Unit)
                    }

                    else -> {
                        logger.debug("skipping event v${eventWithVersion.version}")
                        Either.Right(Unit)
                    }
                }
            }

            EventType.REQUEST_TO_BE_SYNCED -> {
                val payload = eventWithVersion.event.eventData as RequestToBeSynced
                return when (payload.game) {
                    Game.WOW_HC -> {
                        either {
                            logger.debug("processing event v${eventWithVersion.version}")
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
                            wowHardcoreEntityCacheService.synchronize(entities.map { it.first })
                        }
                    }

                    else -> {
                        logger.debug("skipping event v${eventWithVersion.version}")
                        Either.Right(Unit)
                    }
                }
            }

            else -> {
                logger.debug(
                    "skipping event v{} ({})",
                    eventWithVersion.version,
                    eventWithVersion.event.eventData.eventType
                )
                Either.Right(Unit)
            }
        }
    }
}

