package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.entities.EntitiesService
import com.kos.eventsourcing.events.EventType
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.ViewDeletedEvent
import com.kos.eventsourcing.subscriptions.EventProcessOutcome

class EntitiesEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val entitiesService: EntitiesService,
) : EventProcessor, WithLogger("eventSubscription.entitiesProcessor") {

    override suspend fun process(): Either<ServiceError, EventProcessOutcome> {
        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_DELETED -> {
                val payload = eventWithVersion.event.eventData as ViewDeletedEvent
                Either.catch {
                    payload.entities.forEach { entityId ->
                        val views = entitiesService.getViewsFromEntity(entityId, payload.game)
                        if (views.isEmpty()) {
                            logger.debug("Deleting entity $entityId")
                            entitiesService.delete(entityId)
                        } else logger.debug(
                            "Not deleting character {} because it's still in {}",
                            entityId,
                            views
                        )
                    }
                }.onLeft { e -> logger.error("failed cleaning up entities for deleted view: ${e.message}") }
                Either.Right(EventProcessOutcome.Processed)
            }

            else -> Either.Right(EventProcessOutcome.Skipped)
        }
    }
}
