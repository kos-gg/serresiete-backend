package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.eventsourcing.events.*
import com.kos.views.ViewsService

class ViewsEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val viewsService: ViewsService
) : EventProcessor, WithLogger("eventSubscription.viewsProcessor") {

    override suspend fun process(): Either<ServiceError, Unit> {
        val operationId = eventWithVersion.event.operationId
        val aggregateRoot = eventWithVersion.event.aggregateRoot
        logger.debug("processing event v${eventWithVersion.version}")

        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_TO_BE_CREATED ->
                viewsService.createView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeCreatedEvent
                ).map { }

            EventType.VIEW_TO_BE_EDITED ->
                viewsService.editView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeEditedEvent
                ).map { }

            EventType.VIEW_TO_BE_PATCHED ->
                viewsService.patchView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBePatchedEvent
                ).map { }

            EventType.VIEW_TO_BE_DELETED ->
                viewsService.deleteView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeDeletedEvent
                ).map { }

            else -> {
                logger.debug("skipping event v${eventWithVersion.version} (${eventWithVersion.event.eventData.eventType})")
                Either.Right(Unit)
            }
        }
    }
}
