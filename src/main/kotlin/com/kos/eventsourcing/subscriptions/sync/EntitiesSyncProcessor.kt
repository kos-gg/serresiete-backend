package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.ControllerError
import com.kos.common.WithLogger
import com.kos.entities.EntitiesService
import com.kos.eventsourcing.events.EventType
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.ViewDeletedEvent

class EntitiesSyncProcessor(
    private val eventWithVersion: EventWithVersion,
    private val entitiesService: EntitiesService,
) : SyncProcessor, WithLogger("eventSubscription.entitiesProcessor") {

    override suspend fun sync(): Either<ControllerError, Unit> {
        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_DELETED -> {
                val payload = eventWithVersion.event.eventData as ViewDeletedEvent
                Either.Right(payload.entities.map { it to entitiesService.getViewsFromEntity(it, payload.game) }
                    .forEach {
                        if (it.second.isEmpty()) {
                            logger.debug("Deleting entity ${it.first}")
                            entitiesService.delete(it.first)
                        } else logger.debug(
                            "Not deleting character {} because it's still in {}",
                            it.first,
                            it.second
                        )

                    })
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