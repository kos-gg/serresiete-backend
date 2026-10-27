package com.kos.clients

import arrow.core.Either
import arrow.core.getOrElse


sealed class ClientError

data class HttpError(val status: Int, val body: String?) : ClientError()
data class JsonParseError(val raw: String, val error: String) : ClientError()
data class TimeoutError(val message: String) : ClientError()
data class NetworkError(val message: String) : ClientError()
data object NotFound : ClientError()

fun <T> Either<ClientError, T>.orThrow(): T = getOrElse { error("Third party call failed: $it") }
