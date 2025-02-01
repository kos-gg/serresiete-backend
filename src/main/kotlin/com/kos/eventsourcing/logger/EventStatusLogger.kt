package com.kos.eventsourcing.logger

import com.kos.eventsourcing.logger.repository.EventLoggerRepository


enum class EventStatus {
    PENDING {
        override fun toString(): String = "pending"
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

    suspend fun logStatus(operationId: String, status: EventStatus): Boolean {
        return repository.insert(operationId, status) ?: false
    }

    suspend fun getStatusHistory(operationId: String): List<EventStatus> {
        return repository.get(operationId) ?: listOf(EventStatus.PENDING)
    }

    suspend fun getSummaryStatus(operationId: String): EventStatus {
        return getStatusHistory(operationId).fold(EventStatus.PENDING) { acc, status ->
            when {
                acc == EventStatus.ERROR -> EventStatus.ERROR
                status == EventStatus.ERROR -> EventStatus.ERROR
                else -> status
            }
        }
    }
}