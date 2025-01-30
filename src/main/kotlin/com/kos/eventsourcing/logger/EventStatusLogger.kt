package com.kos.eventsourcing.logger

import com.kos.eventsourcing.logger.repository.EventLoggerRepository
import com.kos.eventsourcing.events.Operation


enum class EventStatus {
    PENDING {
        override fun toString(): String = "pending"
    },
    UNPROCESSED {
        override fun toString(): String = "unprocessed"
    },
    PROCESSING {
        override fun toString(): String = "processing"
    },
    SUCCESS {
        override fun toString(): String = "success"
    },
    ERROR {
        override fun toString(): String = "error"
    }
}

class EventStatusLogger(
    private val repository: EventLoggerRepository
) {

    suspend fun logStatus(eventId: String, status: EventStatus): Operation {
        return repository.insert(eventId, status)
    }

    suspend fun getStatusHistory(eventId: String): List<EventStatus> {
        return repository.get(eventId) ?: listOf(EventStatus.UNPROCESSED)
    }

    suspend fun getSummaryStatus(eventId: String): EventStatus {
        return getStatusHistory(eventId).fold(EventStatus.PENDING) { acc, status ->
            when {
                acc == EventStatus.ERROR -> EventStatus.ERROR
                status == EventStatus.ERROR -> EventStatus.ERROR
                else -> status
            }
        }
    }
}