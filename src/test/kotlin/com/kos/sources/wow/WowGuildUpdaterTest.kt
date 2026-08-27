package com.kos.sources.wow

import arrow.core.Either
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.domain.WowCharacterResponse
import com.kos.clients.domain.WowGuildResponse
import com.kos.clients.domain.WowMemberResponse
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.GuildPayload
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WowGuildUpdaterTest {
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val viewsRepository = ViewsInMemoryRepository()
    private val resolver = WowEntityResolver(entitiesRepository, raiderIoClient, blizzardClient)
    private val updater = WowGuildUpdater(resolver, entitiesRepository, viewsRepository)

    private val region = "eu"
    private val realm = "twisting-nether"
    private val guildName = "Method"
    private val blizzardId = 999L

    private suspend fun createGuildView(id: String): String {
        viewsRepository.create(id, "$guildName roster", "sanxei", listOf(), Game.WOW, false, null)
        return id
    }

    private suspend fun alreadyTracked(name: String, viewId: String): Long {
        val request = WowEntityRequest(name, region, realm)
        val entity = entitiesRepository.insert(listOf(request), Game.WOW).fold({ error(it.toString()) }, { it }).first()
        viewsRepository.associateEntitiesIdsToView(listOf(entity.id to null), viewId)
        return entity.id
    }

    @Test
    fun `update adds newly joined members, keeps current ones, and removes members who left the guild`() {
        runBlocking {
            val viewId = createGuildView("guild-view")

            val stayingId = alreadyTracked("staying", viewId)
            val leavingId = alreadyTracked("leaving", viewId)
            val joining = WowEntityRequest("joining", region, realm)

            `when`(blizzardClient.getRetailGuildRoster(region, realm, guildName)).thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(
                            WowMemberResponse(WowCharacterResponse("staying", 90)),
                            WowMemberResponse(WowCharacterResponse(joining.name, 90))
                        ),
                        WowGuildResponse(blizzardId)
                    )
                )
            )
            `when`(raiderIoClient.getScore(joining)).thenReturn(Either.Right(1500.0))

            val errors = updater.update(listOf(GuildPayload(guildName, realm, region, blizzardId) to viewId))

            assertEquals(emptyList(), errors)

            val joiningEntity = entitiesRepository.get(joining as EntityRequest, Game.WOW)!!
            val finalEntitiesIds = viewsRepository.get(viewId)!!.entitiesIds.toSet()

            assertEquals(setOf(stayingId, joiningEntity.id), finalEntitiesIds)
            assertTrue(leavingId !in finalEntitiesIds)
        }
    }

    @Test
    fun `update processes multiple guilds independently, each only touching its own view`() {
        runBlocking {
            val viewIdA = createGuildView("guild-view-a")
            val viewIdB = createGuildView("guild-view-b")

            val memberA = WowEntityRequest("membera", region, realm)
            val memberB = WowEntityRequest("memberb", region, realm)

            `when`(blizzardClient.getRetailGuildRoster(region, realm, "GuildA")).thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(WowMemberResponse(WowCharacterResponse(memberA.name, 90))),
                        WowGuildResponse(1L)
                    )
                )
            )
            `when`(blizzardClient.getRetailGuildRoster(region, realm, "GuildB")).thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(WowMemberResponse(WowCharacterResponse(memberB.name, 90))),
                        WowGuildResponse(2L)
                    )
                )
            )
            `when`(raiderIoClient.getScore(memberA)).thenReturn(Either.Right(1500.0))
            `when`(raiderIoClient.getScore(memberB)).thenReturn(Either.Right(1500.0))

            val errors = updater.update(
                listOf(
                    GuildPayload("GuildA", realm, region, 1L) to viewIdA,
                    GuildPayload("GuildB", realm, region, 2L) to viewIdB
                )
            )

            assertEquals(emptyList(), errors)

            val memberAEntity = entitiesRepository.get(memberA as EntityRequest, Game.WOW)!!
            val memberBEntity = entitiesRepository.get(memberB as EntityRequest, Game.WOW)!!

            assertEquals(setOf(memberAEntity.id), viewsRepository.get(viewIdA)!!.entitiesIds.toSet())
            assertEquals(setOf(memberBEntity.id), viewsRepository.get(viewIdB)!!.entitiesIds.toSet())
        }
    }

    @Test
    fun `update reports a guild's resolution failure without blocking the other guilds`() {
        runBlocking {
            val brokenViewId = createGuildView("broken-guild-view")
            val healthyViewId = createGuildView("healthy-guild-view")

            val healthyMember = WowEntityRequest("healthymember", region, realm)

            `when`(blizzardClient.getRetailGuildRoster(region, realm, "BrokenGuild"))
                .thenReturn(Either.Left(TimeoutError("Request timeout has expired")))
            `when`(blizzardClient.getRetailGuildRoster(region, realm, "HealthyGuild")).thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(WowMemberResponse(WowCharacterResponse(healthyMember.name, 90))),
                        WowGuildResponse(2L)
                    )
                )
            )
            `when`(raiderIoClient.getScore(healthyMember)).thenReturn(Either.Right(1500.0))

            val errors = updater.update(
                listOf(
                    GuildPayload("BrokenGuild", realm, region, 1L) to brokenViewId,
                    GuildPayload("HealthyGuild", realm, region, 2L) to healthyViewId
                )
            )

            assertEquals(1, errors.size)

            val healthyMemberEntity = entitiesRepository.get(healthyMember as EntityRequest, Game.WOW)!!
            assertEquals(setOf(healthyMemberEntity.id), viewsRepository.get(healthyViewId)!!.entitiesIds.toSet())
        }
    }
}
