package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.assertTrue
import com.kos.clients.ClientError
import com.kos.clients.domain.*
import com.kos.clients.raiderio.RaiderioHttpClientHelper.client
import com.kos.clients.raiderio.RaiderioHttpClientHelper.raiderioProfileResponse
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.domain.WowEntity
import com.kos.entities.domain.WowEntityRequest
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.fail

class RaiderIoHTTPClientTest {

    private val raiderIoClient = RaiderIoHTTPClient(client)

    @Test
    fun `test get() method with successful response`() {
        runBlocking {
            val result: Either<ClientError, RaiderIoResponse> = raiderIoClient.get(
                WowEntity(1, "region", "realm", "name", null)
            )
            assertEquals(Either.Right(raiderioProfileResponse), result)
        }
    }

    @Test
    fun `test getExpansionSeasons() method with successful response`() {
        runBlocking {
            val result: Either<ClientError, ExpansionSeasons> = raiderIoClient.getExpansionSeasons(10)
            result.onLeft { fail() }
            result.onRight {
                assertEquals(it.seasons[0].name, "TWW Season 3")
                assertTrue(it.seasons[0].isCurrentSeason)
                assertTrue(it.seasons[0].dungeons.contains(Dungeon("Eco-Dome Al'dani", "EDA", 542)))
            }
        }
    }

    @Test
    fun `test exists() method with successful response`() {
        runBlocking {
            assertTrue(
                raiderIoClient.exists(
                    WowEntityRequest(
                        basicWowEntity.name,
                        basicWowEntity.region,
                        basicWowEntity.realm
                    )
                )
            )
        }
    }

    @Test
    fun `test  cutoff() method with successful response`() {
        runBlocking {
            assertEquals(Either.Right(RaiderIoCutoff(1860760)), raiderIoClient.cutoff())
        }
    }

    @Test
    fun `test wowheadEmbeddedCalculator() method with successful response`() {
        runBlocking {
            val result: Either<ClientError, RaiderioWowHeadEmbeddedResponse> =
                raiderIoClient.wowheadEmbeddedCalculator(WowEntity(1, "Surmana", "eu", "Soulseeker", null))
            result.onLeft { fail() }
            result.onRight {
                assertNotNull(it.talentLoadout)
            }
        }
    }

}
