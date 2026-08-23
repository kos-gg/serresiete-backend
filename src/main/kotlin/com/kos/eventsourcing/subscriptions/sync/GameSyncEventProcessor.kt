package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.error.SyncProcessingError
import com.kos.common.error.toEntityResolverError
import com.kos.entities.EntitiesService
import com.kos.entities.sync.EntitySynchronizer
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStore
import com.kos.eventsourcing.subscriptions.EventProcessOutcome

class GameSyncEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val entitiesService: EntitiesService,
    private val synchronizer: EntitySynchronizer,
    private val eventStore: EventStore
) : EventProcessor, WithLogger("eventSubscription.syncEntitiesProcessor") {

    private val game = synchronizer.game

    override suspend fun process(): Either<ServiceError, EventProcessOutcome> {
        val operationId = eventWithVersion.event.operationId
        val aggregateRoot = eventWithVersion.event.aggregateRoot

        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_CREATED -> {
                val payload = eventWithVersion.event.eventData as ViewCreatedEvent
                when (payload.game) {
                    game -> synchronizeView(payload.id, payload.entities, operationId, aggregateRoot)
                    else -> Either.Right(EventProcessOutcome.Skipped)
                }
            }

            EventType.VIEW_EDITED -> {
                val payload = eventWithVersion.event.eventData as ViewEditedEvent
                when (payload.game) {
                    game -> synchronizeView(payload.id, payload.entities, operationId, aggregateRoot)
                    else -> Either.Right(EventProcessOutcome.Skipped)
                }
            }

            EventType.VIEW_PATCHED -> {
                val payload = eventWithVersion.event.eventData as ViewPatchedEvent
                when (payload.game) {
                    game -> synchronizeView(payload.id, payload.entities, operationId, aggregateRoot)
                    else -> Either.Right(EventProcessOutcome.Skipped)
                }
            }

            EventType.REQUEST_TO_BE_SYNCED -> {
                val payload = eventWithVersion.event.eventData as RequestToBeSynced
                when (payload.game) {
                    game -> either {
                        logger.debug("processing event v${eventWithVersion.version}")
                        val resolved = entitiesService.resolveEntities(listOf(payload.request), payload.game).bind()
                        if (resolved.unchecked.isNotEmpty()) {
                            logger.warn("Could not verify existence for entities, they will be skipped: ${resolved.unchecked}")
                        }
                        val inserted = entitiesService
                            .insert(resolved.entities.map { it.first }, payload.game)
                            .mapLeft { it.toEntityResolverError(payload.game, it.message) }
                            .bind()
                        val entities = inserted.zip(resolved.entities.map { it.second }) + resolved.existing
                        val errors = synchronizer.synchronize(entities.map { it.first })
                        if (errors.isNotEmpty()) raise(
                            SyncProcessingError(game.name, errors.joinToString("; ") { it.error() })
                        )
                        EventProcessOutcome.Processed
                    }

                    else -> Either.Right(EventProcessOutcome.Skipped)
                }
            }

            else -> Either.Right(EventProcessOutcome.Skipped)
        }
    }

    private suspend fun synchronizeView(
        viewId: String,
        entities: List<Long>?,
        operationId: String,
        aggregateRoot: String
    ): Either<ServiceError, EventProcessOutcome> = either {
        logger.debug("processing event v${eventWithVersion.version}")
        //TODO: what if no entities?
        val resolved = entities?.mapNotNull { entitiesService.get(it, game) } ?: emptyList()
        val errors = synchronizer.synchronize(resolved)
        if (errors.isNotEmpty()) raise(SyncProcessingError(game.name, errors.joinToString("; ") { it.error() }))
        eventStore.save(Event(aggregateRoot, operationId, ViewSyncCompletedEvent(viewId)))
        EventProcessOutcome.Processed
    }
}
