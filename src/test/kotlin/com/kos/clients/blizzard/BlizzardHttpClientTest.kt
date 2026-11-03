package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.JsonParseError
import com.kos.clients.NotFound
import com.kos.clients.config.RetryConfig
import com.kos.clients.config.blizzardHttpClientConfig
import com.kos.clients.blizzard.BlizzardHttpClientHelper.client
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponse
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import io.ktor.client.*
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.kos.datacache.domain.ClassSpec
import com.kos.datacache.domain.RunDetails
import com.kos.datacache.domain.RunMember
import com.kos.sources.wow.KeystoneBestRun
import com.kos.sources.wow.MythicKeystoneProfile
import com.kos.entities.domain.WowCharacter

class BlizzardHttpClientTest {

    private val blizzardAuthClient = mock(BlizzardAuthClient::class.java)
    private val blizzardClient = BlizzardHttpClient(HttpClient(client.engine) { blizzardHttpClientConfig(RetryConfig(0, 0), blizzardAuthClient) })

    @Test
    fun `getCharacterProfile returns successful response`() {
        runBlocking {
            givenAValidToken()
            val result: Either<ClientError, WowCharacter?> = blizzardClient.getClassicProfile(
                "region", "realm", "name"
            )
            assertEquals(Either.Right(getWowCharacterResponse.toWowCharacter()), result)
        }
    }

    @Test
    fun `getClassicProfile returns not found when blizzard doesn't find the character`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Left(NotFound), blizzardClient.getClassicProfile("region", "realm", "unknown"))
    }

    @Test
    fun `getRetailProfile returns not found when blizzard doesn't find the character`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Left(NotFound), blizzardClient.getRetailProfile("eu", "realm", "unknown"))
    }

    @Test
    fun `getRetailGuildRoster returns not found when blizzard doesn't find the guild`() = runBlocking {
        givenAValidToken()

        assertEquals(Either.Left(NotFound), blizzardClient.getRetailGuildRoster("us", "realm", "unknown"))
    }

    @Test
    fun `getCharacterMedia returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterMedia("region", "realm", "name")

        assertEquals(
            BlizzardMockHelper.getCharacterMedia(basicWowEntity),
            result
        )
    }

    @Test
    fun `getCharacterEquipment returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterEquipment("region", "realm", "name")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowEquipmentResponse.toEquippedItemRefs()),
            result
        )
    }

    @Test
    fun `getCharacterSpecializations returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterSpecializations("region", "realm", "name")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowSpecializationsResponse.toActiveSpecializations()),
            result
        )
    }

    @Test
    fun `getCharacterStats returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getCharacterStats("region", "realm", "name")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowStatsResponse.toWowStats()),
            result
        )
    }

    @Test
    fun `getItemMedia returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getItemMedia("region", 123L)

        assertEquals(
            BlizzardMockHelper.getItemMedia(),
            result
        )
    }

    @Test
    fun `getItem returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getItem("region", 18421)

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowItemResponse.toWowItemDetails()),
            result
        )
    }

    @Test
    fun `getRealm returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getRealm("region", 1L)

        assertEquals(
            Either.Right(BlizzardMockHelper.notHardcoreRealm.toWowRealm()),
            result
        )
    }

    @Test
    fun `getGuildRoster returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getHardcoreGuildRoster("region", "realm", "guild")

        assertEquals(
            Either.Right(BlizzardMockHelper.getWowGuildRosterResponse.toGuildRoster()),
            result
        )
    }


    @Test
    fun `getRetailGuildRoster returns successful response`() = runBlocking {
        givenAValidToken()

        val result =
            blizzardClient.getRetailGuildRoster("us", "realm", "guild")

        assertEquals(Either.Right(BlizzardMockHelper.getWowRetailGuildRosterResponse.toGuildRoster()), result)
    }

    @Test
    fun `getMythicKeystoneProfile returns the rating and the members of each best run`() = runBlocking {
        givenAValidToken()

        val sanxei = RunMember("Sanxei", "zuljin", "Mage", ClassSpec(62, "Arcane", "dps"), "Dwarf", 328)
        val sanxeiTank = RunMember("Sanxeitank", "kazzak", "Paladin", ClassSpec(66, "Protection", "tank"), "Human", 330)

        assertEquals(
            Either.Right(
                MythicKeystoneProfile(
                    rating = 3521.2056,
                    bestRuns = listOf(
                        KeystoneBestRun(249, 11, false, RunDetails(listOf(sanxei.copy(itemLevel = 292)))),
                        KeystoneBestRun(249, 20, true, RunDetails(listOf(sanxei, sanxeiTank)))
                    )
                )
            ),
            blizzardClient.getMythicKeystoneProfile("eu", "realm", "name", 16)
        )
    }

    @Test
    fun `getMythicKeystoneProfile returns no rating and no runs when the character has not played the season`() =
        runBlocking {
            givenAValidToken()

            assertEquals(
                Either.Right(MythicKeystoneProfile(0.0, emptyList())),
                blizzardClient.getMythicKeystoneProfile("eu", "realm", "unrated", 16)
            )
        }

    @Test
    fun `getMythicKeystoneProfile returns not found when blizzard doesn't find the character`() =
        runBlocking {
            givenAValidToken()

            assertEquals(
                Either.Left(NotFound),
                blizzardClient.getMythicKeystoneProfile("eu", "realm", "unknown", 16)
            )
        }

    @Test
    fun `getMythicKeystoneProfile fails when a member has a spec we don't know`() = runBlocking {
        givenAValidToken()

        val result = blizzardClient.getMythicKeystoneProfile("eu", "realm", "unknownspec", 16)

        assertTrue(result.isLeft { it is JsonParseError })
    }

    private suspend fun givenAValidToken() {
        `when`(blizzardAuthClient.getAccessToken())
            .thenReturn(BlizzardMockHelper.getToken())
    }
}
