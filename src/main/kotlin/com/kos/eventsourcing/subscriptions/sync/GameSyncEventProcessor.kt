package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.error.SyncProcessingError
import com.kos.common.error.toEntityResolverError
import com.kos.datacache.EntitySynchronizer
import com.kos.entities.EntitiesService
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore

class GameSyncEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val entitiesService: EntitiesService,
    private val synchronizer: EntitySynchronizer,
    private val eventStore: EventStore
) : EventProcessor, WithLogger("eventSubscription.syncEntitiesProcessor") {

    private val game = synchronizer.game

    override suspend fun process(): Either<ServiceError, Unit> {
        val operationId = eventWithVersion.event.operationId
        val aggregateRoot = eventWithVersion.event.aggregateRoot

        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_CREATED -> {
                val payload = eventWithVersion.event.eventData as ViewCreatedEvent
                when (payload.game) {
                    game -> synchronizeView(payload.id, payload.entities, operationId, aggregateRoot)
                    else -> logger.debug("skipping event v${eventWithVersion.version}")
                }
                Either.Right(Unit)
            }

            EventType.VIEW_EDITED -> {
                val payload = eventWithVersion.event.eventData as ViewEditedEvent
                when (payload.game) {
                    game -> synchronizeView(payload.id, payload.entities, operationId, aggregateRoot)
                    else -> logger.debug("skipping event v${eventWithVersion.version}")
                }
                Either.Right(Unit)
            }

            EventType.VIEW_PATCHED -> {
                val payload = eventWithVersion.event.eventData as ViewPatchedEvent
                when (payload.game) {
                    game -> synchronizeView(payload.id, payload.entities, operationId, aggregateRoot)
                    else -> logger.debug("skipping event v${eventWithVersion.version}")
                }
                Either.Right(Unit)
            }

            EventType.REQUEST_TO_BE_SYNCED -> {
                val payload = eventWithVersion.event.eventData as RequestToBeSynced
                when (payload.game) {
                    game -> {
                        either {
                            logger.debug("processing event v${eventWithVersion.version}")
                            val resolved = entitiesService.resolveEntities(listOf(payload.request), payload.game).bind()
                            val inserted = entitiesService
                                .insert(resolved.entities.map { it.first }, payload.game)
                                .mapLeft { it.toEntityResolverError(payload.game, it.message) }
                                .bind()
                            val entities = inserted.zip(resolved.entities.map { it.second }) + resolved.existing
                            val errors = synchronizer.synchronize(entities.map { it.first })
                            if (errors.isNotEmpty()) raise(
                                SyncProcessingError(
                                    game.name,
                                    errors.joinToString("; ") { it.error() })
                            )
                        }.onLeft { recordFailure(operationId, aggregateRoot, it.error()) }
                        Either.Right(Unit)
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

    private suspend fun synchronizeView(
        viewId: String,
        entities: List<Long>?,
        operationId: String,
        aggregateRoot: String
    ) {
        logger.debug("processing event v${eventWithVersion.version}")
        //TODO: what if no entities?
        val resolved = entities?.mapNotNull { entitiesService.get(it, game) } ?: emptyList()
        val errors = synchronizer.synchronize(resolved)
        if (errors.isNotEmpty()) recordFailure(operationId, aggregateRoot, errors.joinToString("; ") { it.error() })
        else eventStore.save(Event(aggregateRoot, operationId, ViewSyncCompletedEvent(viewId)))
    }

    private suspend fun recordFailure(operationId: String, aggregateRoot: String, reason: String) {
        logger.error("operation $operationId failed: $reason")
        runCatching { eventStore.save(Event(aggregateRoot, operationId, OperationFailedEvent(operationId, reason))) }
            .onFailure { e -> logger.error("failed to store OperationFailedEvent: ${e.message}") }
    }
}
