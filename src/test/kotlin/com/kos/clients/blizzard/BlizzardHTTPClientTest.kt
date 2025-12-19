package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.blizzard.BlizzardHttpClientHelper.client
import com.kos.clients.domain.GetWowCharacterResponse
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponse
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals

class BlizzardHTTPClientTest {

    //TODO - this class should me more exhaustive

    private val blizzardAuthClient = mock(BlizzardAuthClient::class.java)
    private val blizzardClient = BlizzardHttpClient(client, blizzardAuthClient)

    @Test
    fun `test get() method with successful response`() {
        runBlocking {
            `when`(blizzardAuthClient.getAccessToken()).thenReturn(BlizzardMockHelper.getToken())

            val result: Either<ClientError, GetWowCharacterResponse> = blizzardClient.getCharacterProfile(
                "region", "realm", "name"
            )
            assertEquals(Either.Right(getWowCharacterResponse), result)
        }
    }
}
