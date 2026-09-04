package com.kos.sources.wow

import arrow.core.Either
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.domain.WowCharacterResponse
import com.kos.clients.domain.WowGuildResponse
import com.kos.clients.domain.WowMemberResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowRequest
import com.kos.entities.EntitiesTestHelper.basicWowRequest2
import com.kos.entities.domain.GuildPayload
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.views.WowExtraArguments
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class WowEntityResolverTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)

    @Test
    fun `resolves a new character that exists in raiderio`() {
        runBlocking {
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(Either.Right(true))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(basicWowRequest to null), res.entities)
                    assertEquals(listOf(), res.existing)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `does not resolve a new character that does not exist in raiderio`() {
        runBlocking {
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(Either.Right(false))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `a character already in the repository is returned as existing without calling raiderio`() {
        runBlocking {
            val repo =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(basicWowEntity), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(basicWowEntity to null), res.existing)
                    assertEquals(listOf(), res.entities)
                }

            verifyNoInteractions(raiderIoClient)
        }
    }

    @Test
    fun `resolves a batch of new characters concurrently, keeping only the ones that exist`() {
        runBlocking {
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(Either.Right(true))
            `when`(raiderIoClient.exists(basicWowRequest2)).thenReturn(Either.Right(false))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest, basicWowRequest2), null)
                .onLeft { fail() }
                .onRight { res -> assertEquals(listOf(basicWowRequest to null), res.entities) }
        }
    }

    @Test
    fun `resolve reports a raiderio failure as unchecked instead of lying that the character doesn't exist`() {
        runBlocking {
            val timeoutError = TimeoutError("Request timeout has expired")
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(Either.Left(timeoutError))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(1, res.unchecked.size)
                    assertEquals(basicWowRequest, res.unchecked.single().first)
                }
        }
    }

    @Test
    fun `resolve partitions a mixed batch into resolved entities and unchecked ones`() {
        runBlocking {
            val timeoutError = TimeoutError("Request timeout has expired")
            val basicWowRequest3 = basicWowRequest2.copy(name = "thirdcharacter")

            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(Either.Right(true))
            `when`(raiderIoClient.exists(basicWowRequest2)).thenReturn(Either.Right(false))
            `when`(raiderIoClient.exists(basicWowRequest3)).thenReturn(Either.Left(timeoutError))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest, basicWowRequest2, basicWowRequest3), null)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(basicWowRequest to null), res.entities)
                    assertEquals(listOf(basicWowRequest3), res.unchecked.map { it.first })
                }
        }
    }

    private val guildRequest = WowEntityRequest("method", "eu", "twisting-nether")
    private val guildExtraArguments = WowExtraArguments(isGuild = true, season = 0)

    @Test
    fun `resolves a guild's roster, keeping max level members above the score threshold, and returns the guild payload`() {
        runBlocking {
            val member = WowEntityRequest("kakarona", guildRequest.region, guildRequest.realm)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90))),
                            WowGuildResponse(999)
                        )
                    )
                )
            `when`(raiderIoClient.getScore(member)).thenReturn(Either.Right(1500.0))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(member to null), res.entities)
                    assertEquals(listOf(), res.unchecked)
                    assertEquals(
                        GuildPayload(guildRequest.name, guildRequest.realm, guildRequest.region, 999),
                        res.guild
                    )
                }
        }
    }

    @Test
    fun `resolving a guild lowercases the guild payload's name, realm and region`() {
        runBlocking {
            val mixedCaseRequest = WowEntityRequest("Method", "EU", "Twisting-Nether")
            val member = WowEntityRequest("kakarona", mixedCaseRequest.region, mixedCaseRequest.realm)

            `when`(blizzardClient.getRetailGuildRoster(mixedCaseRequest.region, mixedCaseRequest.realm, mixedCaseRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90))),
                            WowGuildResponse(999)
                        )
                    )
                )
            `when`(raiderIoClient.getScore(member)).thenReturn(Either.Right(1500.0))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(mixedCaseRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(GuildPayload("method", "twisting-nether", "eu", 999), res.guild)
                }
        }
    }

    @Test
    fun `filters out guild members below max level before checking their score`() {
        runBlocking {
            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse("lowlevel", 79))),
                            WowGuildResponse(999)
                        )
                    )
                )

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }

            verifyNoInteractions(raiderIoClient)
        }
    }

    @Test
    fun `skips a new guild member with no score instead of resolving it`() {
        runBlocking {
            val member = WowEntityRequest("notcompeting", guildRequest.region, guildRequest.realm)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90))),
                            WowGuildResponse(999)
                        )
                    )
                )
            `when`(raiderIoClient.getScore(member)).thenReturn(Either.Right(0.0))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `a new guild member with any score passes the filter`() {
        runBlocking {
            val member = WowEntityRequest("borderline", guildRequest.region, guildRequest.realm)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90))),
                            WowGuildResponse(999)
                        )
                    )
                )
            `when`(raiderIoClient.getScore(member)).thenReturn(Either.Right(0.1))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(member to null), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `skips a guild member on a raiderio failure while checking their score`() {
        runBlocking {
            val member = WowEntityRequest("flaky", guildRequest.region, guildRequest.realm)
            val timeoutError = TimeoutError("Request timeout has expired")

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90))),
                            WowGuildResponse(999)
                        )
                    )
                )
            `when`(raiderIoClient.getScore(member)).thenReturn(Either.Left(timeoutError))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(), res.entities)
                    assertEquals(listOf(), res.unchecked)
                }
        }
    }

    @Test
    fun `a guild member already tracked is returned as existing without calling raiderio`() {
        runBlocking {
            val member = WowEntityRequest(basicWowEntity.name, guildRequest.region, guildRequest.realm)
            val trackedEntity = member.toEntity(1)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90))),
                            WowGuildResponse(999)
                        )
                    )
                )

            val repo =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(trackedEntity), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(guildRequest), guildExtraArguments)
                .onLeft { fail() }
                .onRight { res ->
                    assertEquals(listOf(trackedEntity to null), res.existing)
                    assertEquals(listOf(), res.entities)
                }

            verifyNoInteractions(raiderIoClient)
        }
    }

    @Test
    fun `a non-guild resolve does not call blizzard`() {
        runBlocking {
            `when`(raiderIoClient.exists(basicWowRequest)).thenReturn(Either.Right(true))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowEntityResolver(repo, raiderIoClient, blizzardClient)

            resolver.resolve(listOf(basicWowRequest), null).onLeft { fail() }

            verifyNoInteractions(blizzardClient)
        }
    }
}
