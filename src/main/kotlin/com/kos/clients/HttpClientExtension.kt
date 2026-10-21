package com.kos.clients

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json

val json = Json {
    ignoreUnknownKeys = true
}

private val accessKeyParameter = Regex("access_key=[^&,\\s\\]]+")

@PublishedApi
internal fun redactAccessKey(message: String): String =
    message.replace(accessKeyParameter, "access_key=***")

suspend inline fun <reified A> fetchFromApi(
    crossinline request: suspend () -> HttpResponse
): Either<ClientError, A> =
    either {
        val response = try {
            request()
        } catch (e: HttpRequestTimeoutException) {
            raise(TimeoutError(redactAccessKey(e.message ?: "Request timeout")))
        }

        ensure(response.status.isSuccess()) {
            HttpError(
                status = response.status.value,
                body = response.bodyAsText()
            )
        }

        val rawBody = response.body<String>()

        try {
            json.decodeFromString<A>(rawBody)
        } catch (e: Exception) {
            raise(
                JsonParseError(
                    raw = rawBody,
                    error = e.stackTraceToString()
                )
            )
        }
    }

suspend inline fun <reified A> fetchFromApi(
    crossinline request: suspend () -> HttpResponse,
    parseResponse: (String) -> A
): Either<ClientError, A> =
    either {
        val response = try {
            request()
        } catch (e: HttpRequestTimeoutException) {
            raise(TimeoutError(redactAccessKey(e.message ?: "Request timeout")))
        }

        ensure(response.status.isSuccess()) {
            HttpError(
                status = response.status.value,
                body = response.bodyAsText()
            )
        }

        val rawBody = response.body<String>()

        try {
            parseResponse(rawBody)
        } catch (e: Exception) {
            raise(
                JsonParseError(
                    raw = rawBody,
                    error = e.stackTraceToString()
                )
            )
        }
    }
