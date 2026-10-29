package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.assertTrue
import com.kos.clients.HttpError
import com.kos.clients.NetworkError
import com.kos.clients.RetryConfig
import com.kos.clients.config.RaiderIoHttpClientConfig
import com.kos.clients.raiderio.RaiderIoHttpClientHelper.ResourceLoader
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class RaiderIoHTTPClientRetryTest {

    private val seasons = ResourceLoader.readResource("unit/wow/raiderio-tww-seasons.json")

    private fun engineResponding(vararg handlers: MockRequestHandler) =
        MockEngine(MockEngineConfig().apply {
            handlers.forEach { addHandler(it) }
            reuseHandlers = false
        })

    private val ok: MockRequestHandler = { respond(seasons, HttpStatusCode.OK) }
    private fun status(code: HttpStatusCode): MockRequestHandler = { respond("", code) }

    @Test
    fun `retries a server error and succeeds`() {
        runBlocking {
            val engine = engineResponding(status(HttpStatusCode.ServiceUnavailable), ok)
            val result = RaiderIoHTTPClient(RaiderIoHttpClientConfig.create(engine, RetryConfig(1, 1), "test-key")).getExpansionSeasons(10)

            assertTrue(result.isRight())
            assertEquals(2, engine.requestHistory.size)
        }
    }

    @Test
    fun `retries a too many requests response`() {
        runBlocking {
            val engine = engineResponding(status(HttpStatusCode.TooManyRequests), ok)
            val result = RaiderIoHTTPClient(RaiderIoHttpClientConfig.create(engine, RetryConfig(1, 1), "test-key")).getExpansionSeasons(10)

            assertTrue(result.isRight())
            assertEquals(2, engine.requestHistory.size)
        }
    }

    @Test
    fun `retries a network failure and succeeds`() {
        runBlocking {
            val engine = engineResponding({ throw IOException("connection reset") }, ok)
            val result = RaiderIoHTTPClient(RaiderIoHttpClientConfig.create(engine, RetryConfig(1, 1), "test-key")).getExpansionSeasons(10)

            assertTrue(result.isRight())
        }
    }

    @Test
    fun `does not retry a client error`() {
        runBlocking {
            val engine = engineResponding(status(HttpStatusCode.BadRequest), ok)
            val result = RaiderIoHTTPClient(RaiderIoHttpClientConfig.create(engine, RetryConfig(1, 1), "test-key")).getExpansionSeasons(10)

            assertEquals(Either.Left(HttpError(400, "")), result)
            assertEquals(1, engine.requestHistory.size)
        }
    }

    @Test
    fun `returns the last server error once retries are exhausted`() {
        runBlocking {
            val engine = engineResponding(
                status(HttpStatusCode.InternalServerError),
                status(HttpStatusCode.BadGateway)
            )
            val result = RaiderIoHTTPClient(RaiderIoHttpClientConfig.create(engine, RetryConfig(1, 1), "test-key")).getExpansionSeasons(10)

            assertEquals(Either.Left(HttpError(502, "")), result)
            assertEquals(2, engine.requestHistory.size)
        }
    }

    @Test
    fun `returns a network error once retries are exhausted`() {
        runBlocking {
            val engine = engineResponding({ throw IOException("connection reset") })
            val result = RaiderIoHTTPClient(RaiderIoHttpClientConfig.create(engine, RetryConfig(0, 1), "test-key")).getExpansionSeasons(10)

            assertEquals(Either.Left(NetworkError("connection reset")), result)
        }
    }

    @Test
    fun `cutoff is never retried`() {
        runBlocking {
            val engine = engineResponding(status(HttpStatusCode.ServiceUnavailable), ok)
            val result = RaiderIoHTTPClient(RaiderIoHttpClientConfig.create(engine, RetryConfig(1, 1), "test-key")).cutoff("season-tww-1")

            assertEquals(Either.Left(HttpError(503, "")), result)
            assertEquals(1, engine.requestHistory.size)
        }
    }

    @Test
    fun `sends the access key and accept header on every request`() {
        runBlocking {
            val engine = engineResponding(ok)
            RaiderIoHTTPClient(RaiderIoHttpClientConfig.create(engine, RetryConfig(0, 1), "test-key")).getExpansionSeasons(10)

            val request = engine.requestHistory.single()
            assertEquals("test-key", request.url.parameters["access_key"])
            assertEquals("10", request.url.parameters["expansion_id"])
            assertEquals("*/*", request.headers[HttpHeaders.Accept])
        }
    }
}
