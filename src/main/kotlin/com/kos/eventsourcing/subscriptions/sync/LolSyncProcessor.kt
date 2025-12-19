package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.error.toEntityResolverError
import com.kos.entities.EntitiesService
import com.kos.entities.cache.LolEntityCacheService
import com.kos.eventsourcing.events.*
import com.kos.views.Game

class LolSyncProcessor(
    private val eventWithVersion: EventWithVersion,
    private val entitiesService: EntitiesService,
    private val lolEntityCacheService: LolEntityCacheService
) : SyncProcessor, WithLogger("eventSubscription.syncLolEntitiesProcessor") {

    override suspend fun sync(): Either<ServiceError, Unit> {
        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_CREATED -> {
                val payload = eventWithVersion.event.eventData as ViewCreatedEvent
                return when (payload.game) {
                    Game.LOL -> {
                        logger.debug("processing event v${eventWithVersion.version}")
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
                        logger.debug("skipping event v${eventWithVersion.version}")
                        Either.Right(Unit)
                    }
                }
            }

            EventType.VIEW_EDITED -> {
                val payload = eventWithVersion.event.eventData as ViewEditedEvent
                return when (payload.game) {
                    Game.LOL -> {
                        logger.debug("processing event v${eventWithVersion.version}")
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
                        logger.debug("skipping event v${eventWithVersion.version}")
                        Either.Right(Unit)
                    }
                }
            }

            EventType.VIEW_PATCHED -> {
                val payload = eventWithVersion.event.eventData as ViewPatchedEvent
                return when (payload.game) {
                    Game.LOL -> {
                        logger.debug("processing event v${eventWithVersion.version}")
                        payload.entities?.mapNotNull { entitiesService.get(it, Game.LOL) }?.let {
                            lolEntityCacheService.cache(it)
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
                    Game.LOL -> {
                        either {
                            logger.debug("processing event v${eventWithVersion.version}")

                            val resolved =
                                entitiesService.resolveEntities(
                                    listOf(payload.request),
                                    payload.game
                                ).bind()

                            val inserted = entitiesService
                                .insert(resolved.entities.map { it.first }, payload.game)
                                .mapLeft {
                                    it.toEntityResolverError(
                                        game = payload.game,
                                        message = "Couldn't insert resolved entities ${resolved.entities}"
                                    )
                                }
                                .bind()

                            val entities = inserted.zip(resolved.entities.map { it.second }) +
                                    resolved.existing

                            lolEntityCacheService.cache(entities.map { it.first })
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