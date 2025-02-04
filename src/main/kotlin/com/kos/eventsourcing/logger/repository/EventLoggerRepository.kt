package com.kos.eventsourcing.logger.repository


import com.kos.common.WithState
import com.kos.eventsourcing.logger.OperationEntry


interface EventLoggerRepository : WithState<Map<String, List<OperationEntry>>, EventLoggerRepository> {
    suspend fun insert(operationId: String, entry: OperationEntry): Boolean?
    suspend fun get(operationId: String): List<OperationEntry>?
}