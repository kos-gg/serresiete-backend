package com.kos.eventsourcing.events.repository

import com.kos.common.InMemoryRepository
import com.kos.eventsourcing.events.Operation
import com.kos.eventsourcing.logger.EventStatus
import com.kos.eventsourcing.logger.repository.EventLoggerRepository

class EventLoggerInMemory : EventLoggerRepository, InMemoryRepository {
    private val eventStatus = mutableMapOf<String, MutableList<EventStatus>>()

    override suspend fun insert(id: String, status: EventStatus): Operation {
        TODO("Not yet implemented")
    }

    override suspend fun get(id: String): List<EventStatus>? {
        TODO("Not yet implemented")
    }

    override suspend fun state(): Map<String, List<EventStatus>> {
        TODO("Not yet implemented")
    }

    override suspend fun withState(initialState: Map<String, List<EventStatus>>): EventLoggerRepository {
        TODO("Not yet implemented")
    }

    override fun clear() {
        eventStatus.clear()
    }

}