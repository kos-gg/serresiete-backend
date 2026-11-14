package com.kos.sources.wowforever

import arrow.core.Either
import com.kos.clients.NotFound
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.entities.domain.WowForeverGuild
import com.kos.entities.domain.WowForeverEntity
import com.kos.entities.domain.WowForeverEntityRequest
import com.kos.entities.domain.WowForeverGuildMember
import com.kos.entities.domain.WowForeverGuildRoster
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.never
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WowForeverGuildResolverTest {
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val region = "eu"
    private val guildName = "TSALAB"
    private val guildId = 104078L
    private val guildRequest = WowForeverEntityRequest(name = guildName, region = region)

    private suspend fun resolverWith(vararg tracked: WowForeverEntity): WowForeverGuildResolver {
        val repo = EntitiesInMemoryRepository().withState(
            EntitiesState(listOf(), listOf(), listOf(), tracked.toList())
        )
        return WowForeverGuildResolver(repo, blizzardClient)
    }

    private suspend fun rosterIs(vararg members: WowForeverGuildMember) {
        `when`(blizzardClient.getForeverGuildRoster(region, guildName))
            .thenReturn(Either.Right(WowForeverGuildRoster(guildId, members.toList())))
    }

    @Test
    fun `every roster member not tracked yet is resolved as a new entity with its blizzard id`() {
        runBlocking {
            rosterIs(WowForeverGuildMember(1, "Sanxei", "Zugzug"), WowForeverGuildMember(2, "Smårips", "Balast"))

            val resolved = resolverWith().resolve(guildRequest)

            assertEquals(
                listOf(
                    WowForeverEntityRequest("sanxei", "zugzug", region, 1) to null,
                    WowForeverEntityRequest("smårips", "balast", region, 2) to null
                ),
                resolved.newMembers
            )
            assertEquals(listOf(), resolved.existing)
        }
    }

    @Test
    fun `a tracked member is reused by blizzard id even when it was renamed`() {
        runBlocking {
            val renamed = WowForeverEntity(10, "oldname", "oldsurname", region, 1)
            rosterIs(WowForeverGuildMember(1, "Sanxei", "Zugzug"), WowForeverGuildMember(2, "Eredeth", "Why"))

            val resolved = resolverWith(renamed).resolve(guildRequest)

            assertEquals(listOf(renamed), resolved.existing)
            assertEquals(listOf(WowForeverEntityRequest("eredeth", "why", region, 2) to null), resolved.newMembers)
        }
    }

    @Test
    fun `a member with the same name as a tracked character but another blizzard id is a new entity`() {
        runBlocking {
            val namesake = WowForeverEntity(10, "sanxei", "zugzug", region, 99)
            rosterIs(WowForeverGuildMember(1, "Sanxei", "Zugzug"))

            val resolved = resolverWith(namesake).resolve(guildRequest)

            assertEquals(listOf(), resolved.existing)
            assertEquals(listOf(WowForeverEntityRequest("sanxei", "zugzug", region, 1) to null), resolved.newMembers)
        }
    }

    @Test
    fun `the guild is returned without realm`() {
        runBlocking {
            rosterIs()

            val resolved = resolverWith().resolve(guildRequest)

            assertEquals(WowForeverGuild("tsalab", region, guildId), resolved.guild)
        }
    }

    @Test
    fun `no character profile is requested while resolving the roster`() {
        runBlocking {
            rosterIs(WowForeverGuildMember(1, "Sanxei", "Zugzug"))

            resolverWith().resolve(guildRequest)

            verify(blizzardClient, never()).getForeverProfile(any(), any(), any())
        }
    }

    @Test
    fun `resolving a guild that doesn't exist fails`() {
        runBlocking {
            `when`(blizzardClient.getForeverGuildRoster(region, guildName)).thenReturn(Either.Left(NotFound))

            val resolver = resolverWith()

            assertFailsWith<IllegalStateException> { resolver.resolve(guildRequest) }
        }
    }

    @Test
    fun `exists returns the guild payload without realm`() {
        runBlocking {
            rosterIs(WowForeverGuildMember(1, "Sanxei", "Zugzug"))

            assertEquals(
                Either.Right(WowForeverGuild("tsalab", region, guildId)),
                resolverWith().exists(guildRequest)
            )
        }
    }

    @Test
    fun `exists returns the client error when blizzard can't be reached`() {
        runBlocking {
            val timeout = TimeoutError("Request timeout has expired")
            `when`(blizzardClient.getForeverGuildRoster(region, guildName)).thenReturn(Either.Left(timeout))

            assertEquals(Either.Left(timeout), resolverWith().exists(guildRequest))
        }
    }
}
