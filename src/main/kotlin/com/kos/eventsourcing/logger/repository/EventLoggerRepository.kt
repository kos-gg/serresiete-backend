package com.kos.eventsourcing.logger.repository


import com.kos.common.WithState
import com.kos.eventsourcing.logger.EventStatus


interface EventLoggerRepository : WithState<Map<String, List<EventStatus>>, EventLoggerRepository> {
    suspend fun insert(operationId: String, status: EventStatus): Boolean?
    suspend fun get(operationId: String): List<EventStatus>?
}