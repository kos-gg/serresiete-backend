package com.kos.common

sealed class ServiceError

data class SyncProcessingError(val type: String, val message: String) : ServiceError()