package com.kos.clients

import com.kos.common.error.ServiceError
import com.kos.common.error.SyncProcessingError


sealed class ClientError

data class HttpError(val status: Int, val body: String?) : ClientError()
data class JsonParseError(val raw: String, val error: String) : ClientError()

fun ClientError.toSyncProcessingError(operation: String): ServiceError =
    when (this) {
        is HttpError -> SyncProcessingError("HTTP_ERROR", "Failed $operation: $status $body")
        is JsonParseError -> SyncProcessingError("JSON_PARSE_ERROR", "JSON parse error on $operation: $error")
    }

