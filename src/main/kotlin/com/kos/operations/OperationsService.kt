package com.kos.operations

import com.kos.eventsourcing.events.EventType
import com.kos.eventsourcing.events.OperationFailedEvent
import com.kos.eventsourcing.events.repository.EventStore

private val completionEventTypes = setOf(
    EventType.VIEW_SYNC_COMPLETED,
    EventType.VIEW_DELETED
)

class OperationsService(private val eventStore: EventStore) {

    suspend fun getOperationStatus(operationId: String): OperationStatus? {
        val events = eventStore.getEventsByOperationId(operationId)
        if (events.isEmpty()) return null

        val failedEvent = events.find { it.event.eventData is OperationFailedEvent }
        val completionEvent = events.find { it.event.eventData.eventType in completionEventTypes }

        return when {
            completionEvent != null -> OperationStatus(
                id = operationId,
                status = OperationStatusType.COMPLETED
            )

            failedEvent != null -> OperationStatus(
                id = operationId,
                status = OperationStatusType.FAILED,
                reason = (failedEvent.event.eventData as OperationFailedEvent).reason
            )

            else -> OperationStatus(
                id = operationId,
                status = OperationStatusType.PENDING
            )
        }
    }
}