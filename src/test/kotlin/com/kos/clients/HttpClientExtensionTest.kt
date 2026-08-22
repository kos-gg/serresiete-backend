package com.kos.clients

import arrow.core.Either
import io.ktor.client.plugins.HttpRequestTimeoutException
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.Serializable
import kotlin.test.Test
import kotlin.test.assertEquals

class HttpClientExtensionTest {

    @Serializable
    data class Dummy(val value: String)

    @Test
    fun `fetchFromApi converts a request timeout into a TimeoutError instead of throwing`() {
        runBlocking {
            val timeoutException = HttpRequestTimeoutException("http://example.com/test", 1000L)

            val result: Either<ClientError, Dummy> = fetchFromApi<Dummy> {
                throw timeoutException
            }

            assertEquals(Either.Left(TimeoutError(timeoutException.message!!)), result)
        }
    }

    @Test
    fun `fetchFromApi with a custom parseResponse also converts a request timeout into a TimeoutError`() {
        runBlocking {
            val timeoutException = HttpRequestTimeoutException("http://example.com/test", 1000L)

            val result: Either<ClientError, String> = fetchFromApi<String>(
                request = { throw timeoutException },
                parseResponse = { it }
            )

            assertEquals(Either.Left(TimeoutError(timeoutException.message!!)), result)
        }
    }
}
