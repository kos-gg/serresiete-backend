package com.kos.sources.wowforever

import arrow.core.Either
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.entities.GuildUpdateFailed
import com.kos.entities.domain.GuildPayload
import com.kos.entities.domain.WowForeverEntity
import com.kos.entities.domain.WowForeverEntityRequest
import com.kos.entities.domain.WowForeverGuildMember
import com.kos.entities.domain.WowForeverGuildRoster
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class WowForeverGuildUpdaterTest {
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val viewsRepository = ViewsInMemoryRepository()
    private val guildResolver = WowForeverGuildResolver(entitiesRepository, blizzardClient)
    private val updater = WowForeverGuildUpdater(guildResolver, entitiesRepository, viewsRepository)

    private val region = "eu"
    private val guildName = "tsalab"
    private val guild = GuildPayload(guildName, "", region, 104078)

    private suspend fun createGuildView(id: String): String {
        viewsRepository.create(id, "$guildName roster", "sanxei", listOf(), Game.WOW_FOREVER, false, null)
        return id
    }

    private suspend fun tracked(name: String, surname: String, blizzardId: Long): Long =
        entitiesRepository.insert(listOf(WowForeverEntityRequest(name, surname, region, blizzardId)), Game.WOW_FOREVER)
            .first().id

    private suspend fun trackedInView(name: String, surname: String, blizzardId: Long, viewId: String): Long {
        val id = tracked(name, surname, blizzardId)
        viewsRepository.associateEntitiesIdsToView(listOf(id to null), viewId)
        return id
    }

    private suspend fun rosterIs(vararg members: WowForeverGuildMember) {
        `when`(blizzardClient.getForeverGuildRoster(region, guildName))
            .thenReturn(Either.Right(WowForeverGuildRoster(guild.blizzardId, members.toList())))
    }

    private suspend fun viewMembers(viewId: String): Set<Long> =
        viewsRepository.get(viewId)!!.entitiesIds.toSet()

    private suspend fun entityWithBlizzardId(blizzardId: Long): WowForeverEntity =
        entitiesRepository.get(Game.WOW_FOREVER).map { it as WowForeverEntity }.single { it.blizzardId == blizzardId }

    @Test
    fun `update adds newly joined members, keeps current ones, and removes members who left the guild`() {
        runBlocking {
            val viewId = createGuildView("guild-view")
            val stayingId = trackedInView("staying", "here", 1, viewId)
            val leavingId = trackedInView("leaving", "gone", 2, viewId)
            rosterIs(WowForeverGuildMember(1, "Staying", "Here"), WowForeverGuildMember(3, "Joining", "New"))

            val failures = updater.update(listOf(guild to viewId))

            val joining = entityWithBlizzardId(3)
            assertEquals(listOf(), failures)
            assertEquals(setOf(stayingId, joining.id), viewMembers(viewId))
            assertTrue(leavingId !in viewMembers(viewId))
        }
    }

    @Test
    fun `a renamed member is renamed in place instead of inserted twice`() {
        runBlocking {
            val viewId = createGuildView("guild-view")
            val renamedId = trackedInView("oldname", "oldsurname", 1, viewId)
            rosterIs(WowForeverGuildMember(1, "Newname", "Newsurname"))

            updater.update(listOf(guild to viewId))

            assertEquals(
                WowForeverEntity(renamedId, "newname", "newsurname", region, 1),
                entitiesRepository.get(renamedId, Game.WOW_FOREVER)
            )
            assertEquals(1, entitiesRepository.get(Game.WOW_FOREVER).size)
            assertEquals(setOf(renamedId), viewMembers(viewId))
        }
    }

    @Test
    fun `a member already tracked by another view who joins the guild is added to the guild view`() {
        runBlocking {
            val viewId = createGuildView("guild-view")
            val trackedElsewhereId = tracked("sanxei", "zugzug", 1)
            rosterIs(WowForeverGuildMember(1, "Sanxei", "Zugzug"))

            updater.update(listOf(guild to viewId))

            assertEquals(setOf(trackedElsewhereId), viewMembers(viewId))
            assertEquals(1, entitiesRepository.get(Game.WOW_FOREVER).size)
        }
    }

    @Test
    fun `every view tracking the guild gets the same membership and the roster is fetched once`() {
        runBlocking {
            val firstViewId = createGuildView("first-view")
            val secondViewId = createGuildView("second-view")
            val leavingId = trackedInView("leaving", "gone", 2, firstViewId)
            viewsRepository.associateEntitiesIdsToView(listOf(leavingId to null), secondViewId)
            rosterIs(WowForeverGuildMember(3, "Joining", "New"))

            updater.update(listOf(guild to firstViewId, guild to secondViewId))

            val joining = entityWithBlizzardId(3)
            assertEquals(setOf(joining.id), viewMembers(firstViewId))
            assertEquals(setOf(joining.id), viewMembers(secondViewId))
            verify(blizzardClient, times(1)).getForeverGuildRoster(region, guildName)
        }
    }

    @Test
    fun `a guild whose roster can't be fetched is reported as failed and its view is left untouched`() {
        runBlocking {
            val viewId = createGuildView("guild-view")
            val memberId = trackedInView("sanxei", "zugzug", 1, viewId)
            `when`(blizzardClient.getForeverGuildRoster(region, guildName))
                .thenReturn(Either.Left(TimeoutError("Request timeout has expired")))

            val failures = updater.update(listOf(guild to viewId))

            assertEquals(1, failures.size)
            assertEquals(guild, (failures.first() as GuildUpdateFailed).guild)
            assertEquals(setOf(memberId), viewMembers(viewId))
        }
    }
}
