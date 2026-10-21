package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.assertTrue
import com.kos.clients.ClientError
import com.kos.clients.RetryConfig
import com.kos.clients.raiderio.RaiderIoHttpClientHelper.client
import com.kos.clients.raiderio.RaiderIoHttpClientHelper.raiderioProfile
import com.kos.entities.domain.WowEntity
import com.kos.sources.wow.staticdata.wowseason.Dungeon
import com.kos.sources.wow.staticdata.wowseason.Season
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import com.kos.sources.wow.SeasonCutoff
import com.kos.sources.wow.WowCharacterProfile

class RaiderIoHTTPClientTest {

    private val raiderIoClient = RaiderIoHTTPClient(client, RetryConfig(0, 0), apiKey = "test-key")

    @Test
    fun `test get() method with successful response`() {
        runBlocking {
            val result: Either<ClientError, WowCharacterProfile> = raiderIoClient.get(
                WowEntity(1, "region", "realm", "name", null)
            )
            assertEquals(Either.Right(raiderioProfile), result)
        }
    }

    @Test
    fun `test getExpansionSeasons() method with successful response`() {
        runBlocking {
            val result: Either<ClientError, List<Season>> = raiderIoClient.getExpansionSeasons(10)
            result.onLeft { fail() }
            result.onRight {
                assertEquals(it[0].name, "TWW Season 3")
                assertTrue(it[0].isCurrentSeason)
                assertTrue(
                    it[0].dungeons.contains(
                        Dungeon(
                            "Eco-Dome Al'dani",
                            "EDA",
                            542,
                            "https://cdn.raiderio.net/images/wow/icons/large/inv_112_achievement_dungeon_ecodome.jpg"
                        )
                    )
                )
            }
        }
    }

    @Test
    fun `test getRunDetails() method with successful response`() {
        runBlocking {
            val result = raiderIoClient.getRunDetails("season-mn-1", "3415343")
            result.onLeft { fail() }
            result.onRight {
                assertEquals(RaiderIoHttpClientHelper.runDetails, it)
                assertEquals(3, it.loggedDetails?.deaths?.size)
            }
        }
    }

    @Test
    fun `getRunDetails() does not retry when the request fails`() {
        runBlocking {
            val failingEngine = MockEngine { respondError(HttpStatusCode.InternalServerError) }
            val failingClient = RaiderIoHTTPClient(HttpClient(failingEngine), RetryConfig(3, 0), apiKey = "test-key")

            val result = failingClient.getRunDetails("season-mn-1", "3415343")

            assertTrue(result.isLeft())
            assertEquals(1, failingEngine.requestHistory.size)
        }
    }

    @Test
    fun `test  cutoff() method with successful response`() {
        runBlocking {
            assertEquals(Either.Right(SeasonCutoff(1860760)), raiderIoClient.cutoff("season-tww-1"))
        }
    }

    @Test
    fun `test wowheadEmbeddedCalculator() method with successful response`() {
        runBlocking {
            val result: Either<ClientError, String> =
                raiderIoClient.wowheadEmbeddedCalculator("eu", "Soulseeker", "Surmana")
            result.onLeft { fail() }
            result.onRight {
                assertTrue(it.isNotEmpty())
            }
        }
    }

}
