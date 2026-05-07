package com.kos.operations

import kotlinx.serialization.Serializable

enum class OperationStatusType { PENDING, COMPLETED, FAILED }

@Serializable
data class OperationStatus(
    val id: String,
    val status: OperationStatusType,
    val resourceId: String? = null,
    val reason: String? = null
)
