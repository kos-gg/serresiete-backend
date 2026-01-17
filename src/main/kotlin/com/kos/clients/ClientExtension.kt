package com.kos.clients

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.ktor.client.call.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

suspend inline fun <reified A> httpCall(
    crossinline request: suspend () -> HttpResponse
): Either<ClientError, A> =
    either {
        val response = request()

        ensure(response.status.isSuccess()) {
            HttpError(
                status = response.status.value,
                body = response.bodyAsText()
            )
        }

        val rawBody = response.body<String>()

        try {
            Json.decodeFromString<A>(rawBody)
        } catch (e: Exception) {
            raise(
                JsonParseError(
                    raw = rawBody,
                    error = e.stackTraceToString()
                )
            )
        }
    }
