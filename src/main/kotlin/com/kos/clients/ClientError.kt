package com.kos.clients

import arrow.core.Either
import com.kos.common.error.ServiceError
import com.kos.common.error.SyncProcessingError


sealed class ClientError

data class HttpError(val status: Int, val body: String?) : ClientError()
data class JsonParseError(val raw: String, val error: String) : ClientError()
data class TimeoutError(val message: String) : ClientError()
data class NetworkError(val message: String) : ClientError()

fun <T> Either<ClientError, T>.orNotFound(fallback: T): Either<ClientError, T> =
    fold(
        ifLeft = { error -> if (error is HttpError && error.status == 404) Either.Right(fallback) else Either.Left(error) },
        ifRight = { Either.Right(it) }
    )

fun ClientError.toSyncProcessingError(operation: String): ServiceError =
    when (this) {
        is HttpError -> SyncProcessingError("HTTP_ERROR", "Failed $operation: $status $body")
        is JsonParseError -> SyncProcessingError("JSON_PARSE_ERROR", "JSON parse error on $operation: $error")
        is TimeoutError -> SyncProcessingError("TIMEOUT_ERROR", "Timeout on $operation: $message")
        is NetworkError -> SyncProcessingError("NETWORK_ERROR", "Network error on $operation: $message")
    }

