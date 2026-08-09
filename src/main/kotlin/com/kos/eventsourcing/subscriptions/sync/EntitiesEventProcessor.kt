package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import arrow.core.NonFatal
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.entities.EntitiesService
import com.kos.eventsourcing.events.EventType
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.ViewDeletedEvent

class EntitiesEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val entitiesService: EntitiesService,
) : EventProcessor, WithLogger("eventSubscription.entitiesProcessor") {

    override suspend fun process(): Either<ServiceError, Unit> {
        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_DELETED -> {
                val payload = eventWithVersion.event.eventData as ViewDeletedEvent
                try {
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
                } catch (e: Exception) {
                    if (!NonFatal(e)) throw e
                    logger.error("failed cleaning up entities for deleted view: ${e.message}")
                }
                Either.Right(Unit)
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
