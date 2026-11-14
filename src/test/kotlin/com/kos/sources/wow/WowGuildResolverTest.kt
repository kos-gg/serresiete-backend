package com.kos.sources.wow

import com.kos.entities.guilds.WowGuild
import arrow.core.Either
import com.kos.clients.NotFound
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.toGuildRoster
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.domain.WowCharacterResponse
import com.kos.clients.domain.WowGuildResponse
import com.kos.clients.domain.WowMemberResponse
import com.kos.clients.domain.WowRosterMemberRealmResponse
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.sources.wow.staticdata.wowseason.WowSeason
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonsState
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.fail

class WowGuildResolverTest {
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val season = WowSeason(16, "Midnight Season 1", "season-mn-1", 11, "{}", true)
    private val seasonRepository = runBlocking { WowSeasonInMemoryRepository().withState(WowSeasonsState(listOf(season))) }
    private val guildResolver = WowGuildResolver(EntitiesInMemoryRepository(), blizzardClient, seasonRepository)
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
                    assertEquals(WowGuild(guildRequest.name, guildRequest.realm, guildRequest.region, 999), guild)
                }

            verify(blizzardClient, never()).getMythicKeystoneProfile(any(), any(), any(), any())
        }
    }

    @Test
    fun `exists returns not found when the guild doesn't exist`() {
        runBlocking {
            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(Either.Left(NotFound))

            assertEquals(Either.Left(NotFound), guildResolver.exists(guildRequest))
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

    @Test
    fun `resolves a guild's roster, keeping max level members above the score threshold, and returns the guild payload`() {
        runBlocking {
            val member = WowEntityRequest("kakarona", guildRequest.region, guildRequest.realm)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 555))),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )
            `when`(blizzardClient.getMythicKeystoneProfile(member.region, member.realm, member.name, season.id)).thenReturn(Either.Right(MythicKeystoneProfile(1500.0, emptyList())))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowGuildResolver(repo, blizzardClient, seasonRepository)

            resolver.resolve(guildRequest)
                .let { res ->
                    assertEquals(
                        listOf(WowEntityRequest(member.name, member.region, member.realm, 555)),
                        res.newMembers
                    )
                    assertEquals(
                        WowGuild(guildRequest.name, guildRequest.realm, guildRequest.region, 999),
                        res.guild
                    )
                }
        }
    }

    @Test
    fun `resolves a guild member on a different connected realm using their own realm, not the guild's`() {
        runBlocking {
            val member = WowEntityRequest("xesevi", guildRequest.region, "coilfang")

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(
                                WowMemberResponse(
                                    WowCharacterResponse(
                                        member.name,
                                        90,
                                        105161061,
                                        WowRosterMemberRealmResponse("coilfang")
                                    )
                                )
                            ),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )
            `when`(blizzardClient.getMythicKeystoneProfile(member.region, member.realm, member.name, season.id)).thenReturn(Either.Right(MythicKeystoneProfile(1500.0, emptyList())))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowGuildResolver(repo, blizzardClient, seasonRepository)

            resolver.resolve(guildRequest)
                .let { res ->
                    assertEquals(
                        listOf(WowEntityRequest(member.name, member.region, member.realm, 105161061)),
                        res.newMembers
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
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 555))),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )
            `when`(blizzardClient.getMythicKeystoneProfile(member.region, member.realm, member.name, season.id)).thenReturn(Either.Right(MythicKeystoneProfile(1500.0, emptyList())))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowGuildResolver(repo, blizzardClient, seasonRepository)

            resolver.resolve(mixedCaseRequest)
                .let { res ->
                    assertEquals(WowGuild("method", "twisting-nether", "eu", 999), res.guild)
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
                            listOf(WowMemberResponse(WowCharacterResponse("lowlevel", 79, 111))),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowGuildResolver(repo, blizzardClient, seasonRepository)

            resolver.resolve(guildRequest)
                .let { res ->
                    assertEquals(listOf(), res.newMembers)
                }

            verify(blizzardClient, never()).getMythicKeystoneProfile(any(), any(), any(), any())
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
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 222))),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )
            `when`(blizzardClient.getMythicKeystoneProfile(member.region, member.realm, member.name, season.id)).thenReturn(Either.Right(MythicKeystoneProfile(0.0, emptyList())))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowGuildResolver(repo, blizzardClient, seasonRepository)

            resolver.resolve(guildRequest)
                .let { res ->
                    assertEquals(listOf(), res.newMembers)
                }
        }
    }

    @Test
    fun `a new guild member with a score at the cutoff passes the filter`() {
        runBlocking {
            val member = WowEntityRequest("borderline", guildRequest.region, guildRequest.realm)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 777))),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )
            `when`(blizzardClient.getMythicKeystoneProfile(member.region, member.realm, member.name, season.id)).thenReturn(Either.Right(MythicKeystoneProfile(1000.0, emptyList())))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowGuildResolver(repo, blizzardClient, seasonRepository)

            resolver.resolve(guildRequest)
                .let { res ->
                    assertEquals(
                        listOf(WowEntityRequest(member.name, member.region, member.realm, 777)),
                        res.newMembers
                    )
                }
        }
    }

    @Test
    fun `skips a guild member on a blizzard failure while checking their rating`() {
        runBlocking {
            val member = WowEntityRequest("flaky", guildRequest.region, guildRequest.realm)
            val timeoutError = TimeoutError("Request timeout has expired")

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 333))),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )
            `when`(blizzardClient.getMythicKeystoneProfile(member.region, member.realm, member.name, season.id)).thenReturn(Either.Left(timeoutError))

            val repo = EntitiesInMemoryRepository().withState(EntitiesState(listOf(), listOf(), listOf()))
            val resolver = WowGuildResolver(repo, blizzardClient, seasonRepository)

            resolver.resolve(guildRequest)
                .let { res ->
                    assertEquals(listOf(), res.newMembers)
                }
        }
    }

    @Test
    fun `a guild member already tracked is returned as existing without checking their rating`() {
        runBlocking {
            val member = WowEntityRequest(basicWowEntity.name, guildRequest.region, guildRequest.realm)
            val trackedEntity = member.toEntity(1)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90, 444))),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )

            val repo =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(trackedEntity), listOf(), listOf()))
            val resolver = WowGuildResolver(repo, blizzardClient, seasonRepository)

            resolver.resolve(guildRequest)
                .let { res ->
                    assertEquals(listOf(trackedEntity), res.existing.map { it.second })
                    assertEquals(listOf(), res.newMembers)
                }

            verify(blizzardClient, never()).getMythicKeystoneProfile(any(), any(), any(), any())
        }
    }

    @Test
    fun `a guild member renamed since it was tracked is found by blizzard id and returned as existing`() {
        runBlocking {
            val trackedEntity = WowEntityRequest("oldname", guildRequest.region, guildRequest.realm, 444).toEntity(1)

            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse("newname", 90, 444))),
                            WowGuildResponse(999)
                        ).toGuildRoster()
                    )
                )

            val repo =
                EntitiesInMemoryRepository().withState(EntitiesState(listOf(trackedEntity), listOf(), listOf()))
            val resolver = WowGuildResolver(repo, blizzardClient, seasonRepository)

            resolver.resolve(guildRequest)
                .let { res ->
                    assertEquals(listOf(trackedEntity), res.existing.map { it.second })
                    assertEquals(listOf(), res.newMembers)
                }

            verify(blizzardClient, never()).getMythicKeystoneProfile(any(), any(), any(), any())
        }
    }

    @Test
    fun `resolving a guild fails when there is no current mythic+ season`() {
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

            val resolver = WowGuildResolver(EntitiesInMemoryRepository(), blizzardClient, WowSeasonInMemoryRepository())

            assertFailsWith<IllegalStateException> { resolver.resolve(guildRequest) }

            verify(blizzardClient, never()).getMythicKeystoneProfile(any(), any(), any(), any())
        }
    }

    @Test
    fun `resolving a guild that blizzard does not know fails with the guild in the message`() {
        runBlocking {
            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(Either.Left(NotFound))

            val error = assertFailsWith<IllegalStateException> { guildResolver.resolve(guildRequest) }

            assertEquals("Guild method (eu/twisting-nether) not found", error.message)
        }
    }

    @Test
    fun `resolving a guild fails with blizzard's error when its roster cannot be fetched`() {
        runBlocking {
            `when`(blizzardClient.getRetailGuildRoster(guildRequest.region, guildRequest.realm, guildRequest.name))
                .thenReturn(Either.Left(TimeoutError("Request timeout has expired")))

            val error = assertFailsWith<IllegalStateException> { guildResolver.resolve(guildRequest) }

            assertEquals("Third party call failed: TimeoutError(message=Request timeout has expired)", error.message)
        }
    }
}
