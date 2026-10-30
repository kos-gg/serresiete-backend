package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.assertTrue
import com.kos.clients.ClientError
import com.kos.clients.RetryConfig
import com.kos.clients.config.raiderIoHttpClientConfig
import com.kos.clients.raiderio.RaiderIoHttpClientHelper.client
import com.kos.clients.raiderio.RaiderIoHttpClientHelper.raiderioProfile
import com.kos.entities.domain.WowEntity
import com.kos.sources.wow.staticdata.wowseason.Dungeon
import com.kos.sources.wow.staticdata.wowseason.Season
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import com.kos.sources.wow.SeasonCutoff
import com.kos.sources.wow.WowCharacterProfile

class RaiderIoHTTPClientTest {

    private val raiderIoClient = RaiderIoHTTPClient(HttpClient(client.engine) { raiderIoHttpClientConfig(RetryConfig(0, 0), apiKey = "test-key") })

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
