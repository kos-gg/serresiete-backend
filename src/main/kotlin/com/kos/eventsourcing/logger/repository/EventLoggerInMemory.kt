package com.kos.eventsourcing.logger.repository

import com.kos.common.InMemoryRepository
import com.kos.eventsourcing.logger.EventStatus

class EventLoggerInMemory : EventLoggerRepository, InMemoryRepository {
    private val eventStatus = mutableMapOf<String, MutableList<EventStatus>>()

    override suspend fun insert(operationId: String, status: EventStatus): Boolean? {
        return eventStatus[operationId]?.add(status)
    }

    override suspend fun get(operationId: String): List<EventStatus>? {
        return eventStatus[operationId]
    }

    override suspend fun state(): Map<String, List<EventStatus>> {
        return eventStatus
    }

    override suspend fun withState(initialState: Map<String, List<EventStatus>>): EventLoggerRepository {
        initialState.forEach { (operationId, statuses) ->
            eventStatus[operationId] = statuses.toMutableList()
        }
        return this
    }

    override fun clear() {
        eventStatus.clear()
    }

}