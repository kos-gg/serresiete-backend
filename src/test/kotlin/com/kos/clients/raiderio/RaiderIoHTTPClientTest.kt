package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.entities.WowEntity
import com.kos.clients.domain.RaiderIoResponse
import com.kos.clients.raiderio.RaiderioHttpClientHelper.client
import com.kos.clients.raiderio.RaiderioHttpClientHelper.raiderioProfileResponse
import com.kos.common.HttpError
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals

class RaiderIoHTTPClientTest {

    private val raiderIoClient = RaiderIoHTTPClient(client)

    //TODO: This suite must be more extensive

    @Test
    fun `test get() method with successful response`() {
        runBlocking {
            val result: Either<HttpError, RaiderIoResponse> = raiderIoClient.get(
                WowEntity(1, "region", "realm", "name", null)
            )
            assertEquals(Either.Right(raiderioProfileResponse), result)
        }
    }
}
