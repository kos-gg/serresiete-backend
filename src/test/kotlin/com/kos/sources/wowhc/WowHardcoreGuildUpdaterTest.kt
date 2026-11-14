package com.kos.sources.wowhc

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
import com.kos.entities.GuildUpdateFailed
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.WowGuild
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals

class WowHardcoreGuildUpdaterTest {
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val viewsRepository = ViewsInMemoryRepository()
    private val resolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
    private val updater = WowHardcoreGuildUpdater(
        WowHardcoreGuildResolver(entitiesRepository, blizzardClient, resolver),
        resolver,
        entitiesRepository,
        viewsRepository
    )

    private val region = basicWowRequest.region
    private val realm = basicWowRequest.realm

    private suspend fun createGuildView(id: String): String {
        viewsRepository.create(id, "sanxei guild roster", "sanxei", listOf(), Game.WOW_HC, false, null)
        return id
    }

    @Test
    fun `update reports a guild's resolution failure without blocking the other guilds`() {
        runBlocking {
            val brokenViewId = createGuildView("broken-guild-view")
            val healthyViewId = createGuildView("healthy-guild-view")
            val brokenGuild = WowGuild("BrokenGuild", realm, region, 1L)
            val brokenGuildError = TimeoutError("Request timeout has expired")

            `when`(blizzardClient.getHardcoreGuildRoster(region, realm, "BrokenGuild"))
                .thenReturn(Either.Left(brokenGuildError))
            `when`(blizzardClient.getHardcoreGuildRoster(region, realm, "HealthyGuild")).thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(WowMemberResponse(WowCharacterResponse(basicWowRequest.name, 60))),
                        WowGuildResponse(2L)
                    ).toGuildRoster()
                )
            )
            `when`(blizzardClient.getClassicProfile(region, realm, basicWowRequest.name))
                .thenReturn(BlizzardMockHelper.getCharacterProfile(basicWowRequest))
            `when`(blizzardClient.getRealm(region, 5220)).thenReturn(Either.Right(hardcoreRealm.toWowRealm()))

            val errors = updater.update(
                listOf(
                    brokenGuild to brokenViewId,
                    WowGuild("HealthyGuild", realm, region, 2L) to healthyViewId
                )
            )

            assertEquals(listOf(brokenGuild), errors.map { (it as GuildUpdateFailed).guild })
            assertEquals(emptyList(), viewsRepository.get(brokenViewId)!!.entitiesIds)

            val healthyMember = entitiesRepository.get(basicWowRequest as EntityRequest, Game.WOW_HC)!!
            assertEquals(listOf(healthyMember.id), viewsRepository.get(healthyViewId)!!.entitiesIds)
        }
    }
}
