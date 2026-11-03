package com.kos.clients

import arrow.core.Either
import arrow.core.raise.Raise
import arrow.core.raise.either
import arrow.core.raise.ensure
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import io.ktor.client.call.*
import io.ktor.client.plugins.*
import io.ktor.client.statement.*
import io.ktor.http.*
import kotlinx.serialization.json.Json
import java.io.IOException

val json = Json {
    ignoreUnknownKeys = true
}

private val accessKeyParameter = Regex("access_key=[^&,\\s\\]]+")

@PublishedApi
internal fun redactAccessKey(message: String): String =
    message.replace(accessKeyParameter, "access_key=***")

@PublishedApi
internal suspend inline fun Raise<ClientError>.send(crossinline request: suspend () -> HttpResponse): HttpResponse =
    try {
        request()
    } catch (e: HttpRequestTimeoutException) {
        raise(TimeoutError(redactAccessKey(e.message ?: "Request timeout")))
    } catch (e: IOException) {
        raise(NetworkError(redactAccessKey(e.message ?: e.javaClass.simpleName)))
    } catch (e: RequestNotPermitted) {
        raise(TimeoutError("Rate limiter timeout: ${e.message}"))
    }

suspend inline fun <reified A> fetchFromApi(
    crossinline request: suspend () -> HttpResponse
): Either<ClientError, A> =
    either {
        val response = send(request)

        ensure(response.status.isSuccess()) {
            if (response.status == HttpStatusCode.NotFound) NotFound
            else HttpError(
                status = response.status.value,
                body = response.bodyAsText()
            )
        }

        val rawBody = response.body<String>()

        try {
            json.decodeFromString<A>(rawBody)
        } catch (e: Exception) {
            raise(JsonParseError(e.message ?: e::class.simpleName.orEmpty()))
        }
    }

suspend inline fun <reified A> fetchFromApi(
    crossinline request: suspend () -> HttpResponse,
    parseResponse: (String) -> A
): Either<ClientError, A> =
    either {
        val response = send(request)

        ensure(response.status.isSuccess()) {
            if (response.status == HttpStatusCode.NotFound) NotFound
            else HttpError(
                status = response.status.value,
                body = response.bodyAsText()
            )
        }

        val rawBody = response.body<String>()

        try {
            parseResponse(rawBody)
        } catch (e: Exception) {
            raise(JsonParseError(e.message ?: e::class.simpleName.orEmpty()))
        }
    }
