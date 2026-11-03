package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.assertTrue
import com.kos.clients.HttpError
import com.kos.clients.RetryConfig
import com.kos.clients.config.raiderIoHttpClientConfig
import com.kos.clients.raiderio.RaiderIoHttpClientHelper.ResourceLoader
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.*
import io.ktor.client.request.*
import io.ktor.http.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals

class RaiderIoHttpClientRetryTest {

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
            val result = RaiderIoHttpClient(HttpClient(engine) { raiderIoHttpClientConfig(RetryConfig(1, 1), "test-key") }).getExpansionSeasons(10)

            assertTrue(result.isRight())
            assertEquals(2, engine.requestHistory.size)
        }
    }

    @Test
    fun `retries a too many requests response`() {
        runBlocking {
            val engine = engineResponding(status(HttpStatusCode.TooManyRequests), ok)
            val result = RaiderIoHttpClient(HttpClient(engine) { raiderIoHttpClientConfig(RetryConfig(1, 1), "test-key") }).getExpansionSeasons(10)

            assertTrue(result.isRight())
            assertEquals(2, engine.requestHistory.size)
        }
    }

    @Test
    fun `retries a network failure and succeeds`() {
        runBlocking {
            val engine = engineResponding({ throw IOException("connection reset") }, ok)
            val result = RaiderIoHttpClient(HttpClient(engine) { raiderIoHttpClientConfig(RetryConfig(1, 1), "test-key") }).getExpansionSeasons(10)

            assertTrue(result.isRight())
        }
    }

    @Test
    fun `does not retry a client error`() {
        runBlocking {
            val engine = engineResponding(status(HttpStatusCode.BadRequest), ok)
            val result = RaiderIoHttpClient(HttpClient(engine) { raiderIoHttpClientConfig(RetryConfig(1, 1), "test-key") }).getExpansionSeasons(10)

            assertEquals(Either.Left(HttpError(400, "")), result)
            assertEquals(1, engine.requestHistory.size)
        }
    }

    @Test
    fun `cutoff is never retried`() {
        runBlocking {
            val engine = engineResponding(status(HttpStatusCode.ServiceUnavailable), ok)
            val result = RaiderIoHttpClient(HttpClient(engine) { raiderIoHttpClientConfig(RetryConfig(1, 1), "test-key") }).cutoff("season-tww-1")

            assertEquals(Either.Left(HttpError(503, "")), result)
            assertEquals(1, engine.requestHistory.size)
        }
    }

    @Test
    fun `retries a request that times out`() {
        runBlocking {
            val engine = engineResponding({ delay(1_000); respond(seasons, HttpStatusCode.OK) }, ok)
            val response = HttpClient(engine) { raiderIoHttpClientConfig(RetryConfig(1, 1), "test-key") }
                .get("https://raider.io/api/v1/mythic-plus/static-data") { timeout { requestTimeoutMillis = 50 } }

            assertEquals(HttpStatusCode.OK, response.status)
        }
    }

    @Test
    fun `sends the access key and accept header on every request`() {
        runBlocking {
            val engine = engineResponding(ok)
            RaiderIoHttpClient(HttpClient(engine) { raiderIoHttpClientConfig(RetryConfig(0, 1), "test-key") }).getExpansionSeasons(10)

            val request = engine.requestHistory.single()
            assertEquals("test-key", request.url.parameters["access_key"])
            assertEquals("10", request.url.parameters["expansion_id"])
            assertEquals("*/*", request.headers[HttpHeaders.Accept])
        }
    }
}
