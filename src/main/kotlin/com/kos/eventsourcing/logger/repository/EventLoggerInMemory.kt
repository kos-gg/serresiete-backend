package com.kos.eventsourcing.logger.repository

import com.kos.common.InMemoryRepository
import com.kos.eventsourcing.logger.OperationEntry


class EventLoggerInMemory : EventLoggerRepository, InMemoryRepository {
    private val operationStatus = mutableMapOf<String, MutableList<OperationEntry>>()

    override suspend fun insert(operationId: String, entry: OperationEntry): Boolean? {
        return operationStatus[operationId]?.add(entry)
    }

    override suspend fun get(operationId: String): List<OperationEntry>? {
        return operationStatus[operationId]
    }

    override suspend fun state(): Map<String, List<OperationEntry>> {
        return operationStatus
    }

    override suspend fun withState(initialState: Map<String, List<OperationEntry>>): EventLoggerRepository {
        initialState.forEach { (operationId, entries) ->
            operationStatus[operationId] = entries.toMutableList()
        }
        return this
    }

    override fun clear() {
        operationStatus.clear()
    }

}