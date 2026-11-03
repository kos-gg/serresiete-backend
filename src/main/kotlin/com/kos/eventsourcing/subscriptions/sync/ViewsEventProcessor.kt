package com.kos.eventsourcing.subscriptions.sync

import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.subscriptions.EventProcessOutcome
import com.kos.eventsourcing.subscriptions.EventProcessOutcome.Processed
import com.kos.eventsourcing.subscriptions.EventProcessOutcome.Skipped
import com.kos.views.ViewsEventService

class ViewsEventProcessor(
    private val eventWithVersion: EventWithVersion,
    private val viewsEventService: ViewsEventService
) : EventProcessor {

    override suspend fun process(): EventProcessOutcome =
        when (val eventData = eventWithVersion.event.eventData) {
            is ViewToBeCreatedEvent -> Processed(viewsEventService.createView(eventData))
            is ViewToBeEditedEvent -> Processed(viewsEventService.editView(eventData))
            is ViewToBePatchedEvent -> Processed(viewsEventService.patchView(eventData))
            is ViewToBeDeletedEvent -> Processed(viewsEventService.deleteView(eventData))
            else -> Skipped
        }
}
