package com.kos.eventsourcing.logger.repository


import com.kos.common.WithState
import com.kos.eventsourcing.events.Operation
import com.kos.eventsourcing.logger.EventStatus


interface EventLoggerRepository : WithState<Map<String, List<EventStatus>>, EventLoggerRepository> {
    suspend fun insert(id: String, status: EventStatus): Operation
    suspend fun get(id: String): List<EventStatus>?
}