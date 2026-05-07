package com.kos.eventsourcing.events.repository

import com.kos.common.WithState
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.Operation

interface EventStore : WithState<List<EventWithVersion>, EventStore> {
    suspend fun save(event: Event): Operation
    suspend fun getEvents(version: Long?): Sequence<EventWithVersion>
    suspend fun getEventsByOperationId(operationId: String): List<EventWithVersion>
}