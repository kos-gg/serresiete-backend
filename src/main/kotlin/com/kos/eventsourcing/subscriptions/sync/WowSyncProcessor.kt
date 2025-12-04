package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.ControllerError
import com.kos.entities.EntitiesService
import com.kos.entities.cache.WowEntityCacheService
import com.kos.eventsourcing.events.*
import com.kos.views.Game
import org.slf4j.Logger
import org.slf4j.LoggerFactory

class WowSyncProcessor(
    private val eventWithVersion: EventWithVersion,
    private val entitiesService: EntitiesService,
    private val wowEntityCacheService: WowEntityCacheService
) : SyncProcessor {
    override val logger: Logger = LoggerFactory.getLogger("eventSubscription.syncWowEntitiesProcessor")

    override suspend fun sync(): Either<ControllerError, Unit> {
        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_CREATED -> {
                val payload = eventWithVersion.event.eventData as ViewCreatedEvent
                return when (payload.game) {
                    Game.WOW -> {
                        logger.debug("processing event v${eventWithVersion.version}")
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
                        logger.debug("skipping event v${eventWithVersion.version}")
                        Either.Right(Unit)
                    }
                }
            }

            EventType.VIEW_EDITED -> {
                val payload = eventWithVersion.event.eventData as ViewEditedEvent
                return when (payload.game) {
                    Game.WOW -> {
                        logger.debug("processing event v${eventWithVersion.version}")
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
                        logger.debug("skipping event v${eventWithVersion.version}")
                        Either.Right(Unit)
                    }
                }
            }

            EventType.VIEW_PATCHED -> {
                val payload = eventWithVersion.event.eventData as ViewPatchedEvent
                return when (payload.game) {
                    Game.WOW -> {
                        logger.debug("processing event v${eventWithVersion.version}")
                        payload.entities?.mapNotNull { entitiesService.get(it, Game.WOW) }?.let {
                            wowEntityCacheService
                                .cache(it)
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
                    Game.WOW -> {
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
                            wowEntityCacheService.cache(entities.map { it.first })
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