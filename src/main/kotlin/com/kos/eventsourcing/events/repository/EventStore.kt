package com.kos.eventsourcing.events.repository

import com.kos.common.WithState
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventError
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.Operation
import com.kos.eventsourcing.events.OperationFailedEvent
import java.time.OffsetDateTime

interface EventStore : WithState<List<EventWithVersion>, EventStore> {
    suspend fun save(event: Event): Operation
    suspend fun getEvents(version: Long?): Sequence<EventWithVersion>
    suspend fun getEventsByOperationId(operationId: String): List<EventWithVersion>
    suspend fun deleteEventsOlderThan(timestamp: OffsetDateTime, upToVersion: Long): Int

    suspend fun saveFailedEvent(failed: EventWithVersion, stackTrace: String): Operation =
        save(
            Event(
                failed.event.aggregateRoot,
                failed.event.operationId,
                OperationFailedEvent(
                    failed.event.operationId,
                    EventError(failed.event.eventData.eventType, failed.version, failed.event.eventData, stackTrace)
                )
            )
        )
}
