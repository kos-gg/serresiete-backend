package com.kos.eventsourcing.subscriptions.sync

import com.kos.common.WithLogger
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.subscriptions.EventProcessOutcome
import com.kos.views.ViewsEventService

class ViewsEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val viewsEventService: ViewsEventService
) : EventProcessor, WithLogger("eventSubscription.viewsProcessor") {

    override suspend fun process(): EventProcessOutcome {
        val operationId = eventWithVersion.event.operationId
        val aggregateRoot = eventWithVersion.event.aggregateRoot
        logger.debug("processing event v${eventWithVersion.version}")

        return when (eventWithVersion.event.eventData.eventType) {
            EventType.VIEW_TO_BE_CREATED -> {
                viewsEventService.createView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeCreatedEvent
                )
                EventProcessOutcome.Processed
            }

            EventType.VIEW_TO_BE_EDITED -> {
                viewsEventService.editView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeEditedEvent
                )
                EventProcessOutcome.Processed
            }

            EventType.VIEW_TO_BE_PATCHED -> {
                viewsEventService.patchView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBePatchedEvent
                )
                EventProcessOutcome.Processed
            }

            EventType.VIEW_TO_BE_DELETED -> {
                viewsEventService.deleteView(
                    operationId,
                    aggregateRoot,
                    eventWithVersion.event.eventData as ViewToBeDeletedEvent
                )
                EventProcessOutcome.Processed
            }

            else -> EventProcessOutcome.Skipped
        }
    }
}
