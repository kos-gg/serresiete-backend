package com.kos.sources.wow

import arrow.core.Either
import com.kos.clients.HttpError
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.toGuildRoster
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.domain.WowCharacterResponse
import com.kos.clients.domain.WowGuildResponse
import com.kos.clients.domain.WowMemberResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.entities.domain.GuildPayload
import com.kos.entities.domain.WowEntityRequest
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class WowGuildResolverTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val guildResolver = WowGuildResolver(raiderIoClient, blizzardClient)
    private val guildRequest = WowEntityRequest("method", "eu", "twisting-nether")

    @Test
    fun `exists returns the guild payload without resolving members`() {
        runBlocking {
            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse("kakarona", 90, 555))),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )

            guildResolver.exists(guildRequest)
                .onLeft { fail() }
                .onRight { guild ->
                    assertEquals(GuildPayload(guildRequest.name, guildRequest.realm, guildRequest.region, 999), guild)
                }

            verifyNoInteractions(raiderIoClient)
        }
    }

    @Test
    fun `exists returns null when the guild doesn't exist`() {
        runBlocking {
            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(Either.Left(HttpError(404, null)))

            guildResolver.exists(guildRequest)
                .onLeft { fail() }
                .onRight { guild -> assertEquals(null, guild) }
        }
    }

    @Test
    fun `exists returns a Left when blizzard can't be reached`() {
        runBlocking {
            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(Either.Left(TimeoutError("Request timeout has expired")))

            guildResolver.exists(guildRequest).onRight { fail() }
        }
    }
}
