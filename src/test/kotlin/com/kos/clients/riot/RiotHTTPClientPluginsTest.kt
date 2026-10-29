package com.kos.clients.riot

import arrow.core.Either
import com.kos.clients.HttpError
import com.kos.clients.RetryConfig
import com.kos.clients.config.RiotHttpClientConfig
import com.kos.datacache.domain.QueueType
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class RiotHTTPClientPluginsTest {

    private fun engineResponding(vararg handlers: MockRequestHandler) =
        MockEngine(MockEngineConfig().apply {
            handlers.forEach { addHandler(it) }
            reuseHandlers = false
        })

    private val ok: MockRequestHandler = { respond("""["EUW1_1"]""", HttpStatusCode.OK) }

    private fun riotClient(engine: MockEngine, retries: Int = 0) =
        RiotHTTPClient(RiotHttpClientConfig.create(engine, RetryConfig(retries, 1), "riot-key"))

    @Test
    fun `sends the api key header on every request`() = runBlocking {
        val engine = engineResponding(ok)

        riotClient(engine).getMatchesByPuuid("puuid", QueueType.SOLO_Q)

        assertEquals("riot-key", engine.requestHistory.single().headers["X-Riot-Token"])
    }

    @Test
    fun `retries a too many requests response and succeeds`() = runBlocking {
        val engine = engineResponding({ respond("", HttpStatusCode.TooManyRequests) }, ok)

        val result = riotClient(engine, retries = 1).getMatchesByPuuid("puuid", QueueType.SOLO_Q)

        assertTrue(result.isRight())
        assertEquals(2, engine.requestHistory.size)
    }

    @Test
    fun `does not retry a forbidden response`() = runBlocking {
        val engine = engineResponding({ respond("", HttpStatusCode.Forbidden) }, ok)

        val result = riotClient(engine, retries = 1).getMatchesByPuuid("puuid", QueueType.SOLO_Q)

        assertEquals(Either.Left(HttpError(403, "")), result)
        assertEquals(1, engine.requestHistory.size)
    }
}
