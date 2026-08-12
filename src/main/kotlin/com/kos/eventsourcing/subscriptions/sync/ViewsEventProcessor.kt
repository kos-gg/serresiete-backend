package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.subscriptions.EventProcessOutcome
import com.kos.views.ViewsService

class ViewsEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val viewsService: ViewsService
) : EventProcessor, WithLogger("eventSubscription.viewsProcessor") {

    override suspend fun process(): Either<ServiceError, EventProcessOutcome> {
        val operationId = eventWithVersion.event.operationId
        val aggregateRoot = eventWithVersion.event.aggregateRoot
        logger.debug("processing event v${eventWithVersion.version}")

        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_TO_BE_CREATED ->
                viewsService.createView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeCreatedEvent
                ).map { EventProcessOutcome.Processed }

            EventType.VIEW_TO_BE_EDITED ->
                viewsService.editView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeEditedEvent
                ).map { EventProcessOutcome.Processed }

            EventType.VIEW_TO_BE_PATCHED ->
                viewsService.patchView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBePatchedEvent
                ).map { EventProcessOutcome.Processed }

            EventType.VIEW_TO_BE_DELETED ->
                viewsService.deleteView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeDeletedEvent
                ).map { EventProcessOutcome.Processed }

            else -> Either.Right(EventProcessOutcome.Skipped)
        }
    }
}
