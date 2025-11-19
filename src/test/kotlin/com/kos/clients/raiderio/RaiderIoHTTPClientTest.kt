package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.assertTrue
import com.kos.clients.domain.Dungeon
import com.kos.clients.domain.ExpansionSeasons
import com.kos.clients.domain.RaiderIoResponse
import com.kos.clients.raiderio.RaiderioHttpClientHelper.client
import com.kos.clients.raiderio.RaiderioHttpClientHelper.raiderioProfileResponse
import com.kos.common.HttpError
import com.kos.entities.WowEntity
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

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

    @Test
    fun `test getExpansionSeasons() method with successful response`() {
        runBlocking {
            val result: Either<HttpError, ExpansionSeasons> = raiderIoClient.getExpansionSeasons(10)
            result.onLeft { fail() }
            result.onRight {
                assertEquals(it.seasons.get(0).name, "TWW Season 3")
                assertTrue(it.seasons.get(0).isCurrentSeason)
                assertTrue(it.seasons.get(0).dungeons.contains(Dungeon("Eco-Dome Al'dani", "EDA", 16104)))
            }
        }
    }

}
