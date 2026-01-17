package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.HttpError
import com.kos.clients.JsonParseError
import com.kos.clients.blizzard.BlizzardHttpAuthClientHelper.client
import com.kos.clients.blizzard.BlizzardHttpAuthClientHelper.httpErrorClient
import com.kos.clients.blizzard.BlizzardHttpAuthClientHelper.jsonErrorClient
import com.kos.clients.domain.BlizzardCredentials
import com.kos.clients.domain.TokenResponse
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.fail


class BlizzardHttpAuthClientTest {
    private val credentials = BlizzardCredentials("client", "secret")

    private val blizzardAuthClient =
        BlizzardHttpAuthClient(client, credentials)
    private val blizzardAuthClientWithHttpError =
        BlizzardHttpAuthClient(httpErrorClient, credentials)
    private val blizzardAuthClientWithJsonError =
        BlizzardHttpAuthClient(jsonErrorClient, credentials)

    @Test
    fun `getAccessToken should return a successful response`() =
        runBlocking {
            val result: Either<ClientError, TokenResponse> =
                blizzardAuthClient.getAccessToken()

            result.fold(
                { fail() },
                {
                    assertEquals("token", it.accessToken)
                }
            )
        }


    @Test
    fun `getAccessToken should fail when response status is not successful`() = runBlocking {
        val result: Either<ClientError, TokenResponse> =
            blizzardAuthClientWithHttpError.getAccessToken()

        result.fold(
            { error ->
                val httpError = error as HttpError
                assertEquals(401, httpError.status)
                assertEquals("invalid credentials", httpError.body)
            },
            {
                fail("Expected HttpError but got success")
            }
        )
    }

    @Test
    fun `getAccessToken should fail when response status is successful but can not be parsed into TokenResponse`() =
        runBlocking {
            val result: Either<ClientError, TokenResponse> =
                blizzardAuthClientWithJsonError.getAccessToken()

            result.fold(
                { error ->
                    error as JsonParseError
                    assertEquals("invalid token", error.raw)
                },
                {
                    fail("Expected JsonParseError but got success")
                }
            )
        }
}