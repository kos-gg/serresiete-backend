package com.kos.clients.riot

import com.kos.clients.RetryConfig
import com.kos.clients.config.riotHttpClientConfig
import com.kos.datacache.domain.QueueType
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RiotHttpClientPluginsTest {

    private fun engineResponding(vararg handlers: MockRequestHandler) =
        MockEngine(MockEngineConfig().apply {
            handlers.forEach { addHandler(it) }
            reuseHandlers = false
        })

    private val ok: MockRequestHandler = { respond("""["EUW1_1"]""", HttpStatusCode.OK) }

    private fun riotClient(engine: MockEngine) =
        RiotHttpClient(HttpClient(engine) { riotHttpClientConfig(RetryConfig(0, 1), "riot-key") })

    @Test
    fun `sends the api key header on every request`() = runBlocking {
        val engine = engineResponding(ok)

        riotClient(engine).getMatchesByPuuid("puuid", QueueType.SOLO_Q)

        assertEquals("riot-key", engine.requestHistory.single().headers["X-Riot-Token"])
    }
}
