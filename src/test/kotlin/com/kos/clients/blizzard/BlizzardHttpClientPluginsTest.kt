package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.HttpError
import com.kos.clients.NetworkError
import com.kos.clients.RetryConfig
import com.kos.clients.blizzard.BlizzardHttpClientHelper.json
import com.kos.clients.config.blizzardHttpClientConfig
import com.kos.clients.domain.TokenResponse
import com.kos.datacache.BlizzardMockHelper.notHardcoreRealm
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import org.mockito.kotlin.mock
import org.mockito.kotlin.times
import org.mockito.kotlin.verifyBlocking
import org.mockito.kotlin.whenever
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BlizzardHttpClientPluginsTest {

    private val blizzardAuthClient = mock<BlizzardAuthClient>()

    private fun token(accessToken: String) = Either.Right(TokenResponse(accessToken, "bearer", 86399, "sub"))

    private fun engineResponding(vararg handlers: MockRequestHandler) =
        MockEngine(MockEngineConfig().apply {
            handlers.forEach { addHandler(it) }
            reuseHandlers = false
        })

    private val ok: MockRequestHandler = { respond(json.encodeToString(notHardcoreRealm), HttpStatusCode.OK) }
    private val unauthorized: MockRequestHandler = { respond("", HttpStatusCode.Unauthorized) }

    private fun blizzardClient(engine: MockEngine) =
        BlizzardHttpClient(HttpClient(engine) { blizzardHttpClientConfig(RetryConfig(0, 1), blizzardAuthClient) })

    @Test
    fun `loads the token once and sends it as bearer on every request`() = runBlocking {
        whenever(blizzardAuthClient.getAccessToken()).thenReturn(token("first"))
        val engine = engineResponding(ok, ok)
        val client = blizzardClient(engine)

        client.getRealm("eu", 1)
        client.getRealm("eu", 1)

        assertEquals(
            listOf("Bearer first", "Bearer first"),
            engine.requestHistory.map { it.headers[HttpHeaders.Authorization] }
        )
        verifyBlocking(blizzardAuthClient, times(1)) { getAccessToken() }
    }

    @Test
    fun `refreshes the token after an unauthorized response and repeats the request`() = runBlocking {
        whenever(blizzardAuthClient.getAccessToken()).thenReturn(token("expired"), token("fresh"))
        val engine = engineResponding(unauthorized, ok)

        val result = blizzardClient(engine).getRealm("eu", 1)

        assertTrue(result.isRight())
        assertEquals(
            listOf("Bearer expired", "Bearer fresh"),
            engine.requestHistory.map { it.headers[HttpHeaders.Authorization] }
        )
    }

    @Test
    fun `returns unauthorized when blizzard token cannot be obtained`() = runBlocking {
        whenever(blizzardAuthClient.getAccessToken()).thenReturn(Either.Left(NetworkError("auth down")))
        val engine = engineResponding(unauthorized)

        val result = blizzardClient(engine).getRealm("eu", 1)

        assertEquals(Either.Left(HttpError(401, "")), result)
        assertEquals(null, engine.requestHistory.single().headers[HttpHeaders.Authorization])
    }
}
