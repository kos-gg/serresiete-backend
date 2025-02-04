package com.kos.eventsourcing.logger

import com.kos.eventsourcing.logger.repository.EventLoggerRepository


enum class OperationStatus {
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

data class OperationEntry(
    val status: OperationStatus,
    val info: String,
    val timestamp: String,
)

class EventStatusLogger(
    private val repository: EventLoggerRepository
) {

    suspend fun logStatus(operationId: String, status: OperationStatus, info: String): Boolean {
        return repository.insert(operationId, OperationEntry(status, info, "todo")) ?: false
    }

    suspend fun getStatusHistory(operationId: String): List<OperationEntry> {
        return repository.get(operationId) ?: listOf()
    }
}