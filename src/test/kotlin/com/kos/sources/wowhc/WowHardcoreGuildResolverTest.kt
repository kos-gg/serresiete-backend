package com.kos.sources.wowhc

import com.kos.entities.guilds.WowGuild
import com.kos.entities.guilds.Guild
import arrow.core.Either
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.toGuildRoster
import com.kos.clients.blizzard.toWowRealm
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.domain.WowCharacterResponse
import com.kos.clients.domain.WowGuildResponse
import com.kos.clients.domain.WowMemberResponse
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.BlizzardMockHelper.hardcoreRealm
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WowHardcoreGuildResolverTest {
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val guildResolver = WowHardcoreGuildResolver(
        entitiesRepository,
        blizzardClient,
        WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
    )
    private val guildRequest = WowEntityRequest("Sanxei Guild", basicWowRequest.region, basicWowRequest.realm)

    @Test
    fun `resolves the roster members as hardcore characters and returns the guild payload`() {
        runBlocking {
            `when`(blizzardClient.getHardcoreGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(basicWowRequest.name, 60))),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )
            `when`(
                blizzardClient.getClassicProfile(basicWowRequest.region, basicWowRequest.realm, basicWowRequest.name)
            ).thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowRequest))
            `when`(blizzardClient.getRealm(basicWowRequest.region, 5220)).thenReturn(Either.Right(hardcoreRealm.toWowRealm()))

            guildResolver.resolve(guildRequest)
                .let { res ->
                    assertEquals(listOf(basicWowRequest.name), res.newMembers.map { it.first.name })
                    assertEquals(WowGuild(guildRequest.name, guildRequest.realm, guildRequest.region, 999), res.guild)
                }
        }
    }

    @Test
    fun `filters out roster members below the minimum level before resolving them`() {
        runBlocking {
            `when`(blizzardClient.getHardcoreGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse("lowlevel", 9))),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )

            guildResolver.resolve(guildRequest)
                .let { res -> assertEquals(listOf(), res.newMembers) }

            verify(blizzardClient, never()).getClassicProfile(anyString(), anyString(), anyString())
        }
    }

    @Test
    fun `resolving a guild fails with blizzard's error when its roster cannot be fetched`() {
        runBlocking {
            `when`(blizzardClient.getHardcoreGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(Either.Left(TimeoutError("Request timeout has expired")))

            val error = assertFailsWith<IllegalStateException> { guildResolver.resolve(guildRequest) }

            assertEquals("Third party call failed: TimeoutError(message=Request timeout has expired)", error.message)
        }
    }
}
