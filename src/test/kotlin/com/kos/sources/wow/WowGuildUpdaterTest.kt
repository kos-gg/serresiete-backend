package com.kos.sources.wow

import com.kos.entities.GuildUpdater
import arrow.core.Either
import com.kos.clients.TimeoutError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.toGuildRoster
import com.kos.clients.domain.GetWowRosterResponse
import com.kos.clients.domain.WowCharacterResponse
import com.kos.clients.domain.WowGuildResponse
import com.kos.clients.domain.WowMemberResponse
import com.kos.clients.domain.WowRosterMemberRealmResponse
import com.kos.entities.GuildUpdateFailed
import com.kos.entities.domain.EntityRequest
import com.kos.entities.domain.WowRealmGuild
import com.kos.entities.domain.WowEntity
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsRepository
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import com.kos.sources.wow.staticdata.wowseason.WowSeason
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonsState
import org.mockito.kotlin.any

class WowGuildUpdaterTest {
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val season = WowSeason(16, "Midnight Season 1", "season-mn-1", 11, "{}", true)
    private val seasonRepository = runBlocking { WowSeasonInMemoryRepository().withState(WowSeasonsState(listOf(season))) }
    private val entitiesRepository = EntitiesInMemoryRepository()
    private val viewsRepository = ViewsInMemoryRepository()
    private val guildResolver = WowGuildResolver(entitiesRepository, blizzardClient, seasonRepository)
    private val updater = GuildUpdater(guildResolver, entitiesRepository, viewsRepository)

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
        val entity = entitiesRepository.insert(listOf(request), Game.WOW).first()
        viewsRepository.associateEntitiesIdsToView(listOf(entity.id to null), viewId)
        return entity.id
    }

    private suspend fun alreadyTrackedWithBlizzardId(
        name: String,
        memberBlizzardId: Long,
        viewId: String,
        memberRealm: String = realm
    ): Long {
        val request = WowEntityRequest(name, region, memberRealm, memberBlizzardId)
        val entity = entitiesRepository.insert(listOf(request), Game.WOW).first()
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
                    ).toGuildRoster()
                )
            )
            `when`(blizzardClient.getMythicKeystoneProfile(joining.region, joining.realm, joining.name, season.id)).thenReturn(Either.Right(MythicKeystoneProfile(1500.0, emptyList())))

            val errors = updater.update(listOf(WowRealmGuild(guildName, realm, region, blizzardId) to viewId))

            assertEquals(emptyList(), errors)

            val joiningEntity = entitiesRepository.get(joining as EntityRequest, Game.WOW)!!
            val finalEntitiesIds = viewsRepository.get(viewId)!!.entitiesIds.toSet()

            assertEquals(setOf(stayingId, joiningEntity.id), finalEntitiesIds)
            assertTrue(leavingId !in finalEntitiesIds)
        }
    }

    @Test
    fun `update resolves a guild tracked by two views once and fans out associate-disassociate to both`() {
        runBlocking {
            val viewIdA = createGuildView("guild-view-a")
            val viewIdB = createGuildView("guild-view-b")

            val stayingId = alreadyTracked("staying", viewIdA)
            viewsRepository.associateEntitiesIdsToView(listOf(stayingId to null), viewIdB)
            val leavingId = alreadyTracked("leaving", viewIdA)
            viewsRepository.associateEntitiesIdsToView(listOf(leavingId to null), viewIdB)
            val joining = WowEntityRequest("joining", region, realm)

            `when`(blizzardClient.getRetailGuildRoster(region, realm, guildName)).thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(
                            WowMemberResponse(WowCharacterResponse("staying", 90)),
                            WowMemberResponse(WowCharacterResponse(joining.name, 90))
                        ),
                        WowGuildResponse(blizzardId)
                    ).toGuildRoster()
                )
            )
            `when`(blizzardClient.getMythicKeystoneProfile(joining.region, joining.realm, joining.name, season.id)).thenReturn(Either.Right(MythicKeystoneProfile(1500.0, emptyList())))

            val errors = updater.update(
                listOf(
                    WowRealmGuild(guildName, realm, region, blizzardId) to viewIdA,
                    WowRealmGuild(guildName, realm, region, blizzardId) to viewIdB
                )
            )

            assertEquals(emptyList(), errors)
            verify(blizzardClient, times(1)).getRetailGuildRoster(region, realm, guildName)
            verify(blizzardClient, times(1)).getMythicKeystoneProfile(joining.region, joining.realm, joining.name, season.id)

            val joiningEntity = entitiesRepository.get(joining as EntityRequest, Game.WOW)!!
            val finalA = viewsRepository.get(viewIdA)!!.entitiesIds.toSet()
            val finalB = viewsRepository.get(viewIdB)!!.entitiesIds.toSet()

            assertEquals(setOf(stayingId, joiningEntity.id), finalA)
            assertEquals(setOf(stayingId, joiningEntity.id), finalB)
            assertTrue(leavingId !in finalA)
            assertTrue(leavingId !in finalB)
        }
    }

    @Test
    fun `update detects a renamed and transferred member by blizzardId and updates it in place instead of duplicating it`() {
        runBlocking {
            val viewId = createGuildView("guild-view")

            val trackedId = alreadyTrackedWithBlizzardId("oldname", 555L, viewId)

            `when`(blizzardClient.getRetailGuildRoster(region, realm, guildName)).thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(
                            WowMemberResponse(
                                WowCharacterResponse("newname", 90, 555L, WowRosterMemberRealmResponse("coilfang"))
                            )
                        ),
                        WowGuildResponse(blizzardId)
                    ).toGuildRoster()
                )
            )

            val errors = updater.update(listOf(WowRealmGuild(guildName, realm, region, blizzardId) to viewId))

            assertEquals(emptyList(), errors)
            verify(blizzardClient, never()).getMythicKeystoneProfile(any(), any(), any(), any())

            val updated = entitiesRepository.get(trackedId, Game.WOW) as WowEntity
            assertEquals("newname", updated.name)
            assertEquals("coilfang", updated.realm)
            assertEquals(555L, updated.blizzardId)

            assertEquals(setOf(trackedId), viewsRepository.get(viewId)!!.entitiesIds.toSet())
            assertEquals(1, entitiesRepository.get(Game.WOW).size)
        }
    }

    @Test
    fun `update adds a member who joined the guild and was already tracked by another view`() {
        runBlocking {
            val guildViewId = createGuildView("guild-view")
            val otherViewId = createGuildView("other-view")

            val joinerId = alreadyTrackedWithBlizzardId("joiner", 777L, otherViewId)

            `when`(blizzardClient.getRetailGuildRoster(region, realm, guildName)).thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(WowMemberResponse(WowCharacterResponse("joiner", 90, 777L, WowRosterMemberRealmResponse(realm)))),
                        WowGuildResponse(blizzardId)
                    ).toGuildRoster()
                )
            )

            val errors = updater.update(listOf(WowRealmGuild(guildName, realm, region, blizzardId) to guildViewId))

            assertEquals(emptyList(), errors)
            verify(blizzardClient, never()).getMythicKeystoneProfile(any(), any(), any(), any())
            assertEquals(setOf(joinerId), viewsRepository.get(guildViewId)!!.entitiesIds.toSet())
            assertEquals(1, entitiesRepository.get(Game.WOW).size)
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
                        listOf(WowMemberResponse(WowCharacterResponse(memberA.name, 90, 111))),
                        WowGuildResponse(1L)
                    ).toGuildRoster()
                )
            )
            `when`(blizzardClient.getRetailGuildRoster(region, realm, "GuildB")).thenReturn(
                Either.Right(
                    GetWowRosterResponse(
                        listOf(WowMemberResponse(WowCharacterResponse(memberB.name, 90, 222))),
                        WowGuildResponse(2L)
                    ).toGuildRoster()
                )
            )
            `when`(blizzardClient.getMythicKeystoneProfile(memberA.region, memberA.realm, memberA.name, season.id)).thenReturn(Either.Right(MythicKeystoneProfile(1500.0, emptyList())))
            `when`(blizzardClient.getMythicKeystoneProfile(memberB.region, memberB.realm, memberB.name, season.id)).thenReturn(Either.Right(MythicKeystoneProfile(1500.0, emptyList())))

            val errors = updater.update(
                listOf(
                    WowRealmGuild("GuildA", realm, region, 1L) to viewIdA,
                    WowRealmGuild("GuildB", realm, region, 2L) to viewIdB
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
                    ).toGuildRoster()
                )
            )
            `when`(blizzardClient.getMythicKeystoneProfile(healthyMember.region, healthyMember.realm, healthyMember.name, season.id)).thenReturn(Either.Right(MythicKeystoneProfile(1500.0, emptyList())))

            val errors = updater.update(
                listOf(
                    WowRealmGuild("BrokenGuild", realm, region, 1L) to brokenViewId,
                    WowRealmGuild("HealthyGuild", realm, region, 2L) to healthyViewId
                )
            )

            assertEquals(1, errors.size)

            val healthyMemberEntity = entitiesRepository.get(healthyMember as EntityRequest, Game.WOW)!!
            assertEquals(setOf(healthyMemberEntity.id), viewsRepository.get(healthyViewId)!!.entitiesIds.toSet())
        }
    }

    @Test
    fun `update reports a guild that fails while being stored without blocking the other guilds`() {
        runBlocking {
            val brokenViewId = createGuildView("broken-guild-view")
            val healthyViewId = createGuildView("healthy-guild-view")
            val failingViewsRepository = object : ViewsRepository by viewsRepository {
                override suspend fun associateEntitiesIdsToView(entities: List<Pair<Long, String?>>, id: String) =
                    if (id == brokenViewId) error("connection lost")
                    else viewsRepository.associateEntitiesIdsToView(entities, id)
            }
            val updater = GuildUpdater(guildResolver, entitiesRepository, failingViewsRepository)

            val brokenMember = WowEntityRequest("brokenmember", region, realm)
            val healthyMember = WowEntityRequest("healthymember", region, realm)
            listOf("BrokenGuild" to brokenMember, "HealthyGuild" to healthyMember).forEachIndexed { index, (guild, member) ->
                `when`(blizzardClient.getRetailGuildRoster(region, realm, guild)).thenReturn(
                    Either.Right(
                        GetWowRosterResponse(
                            listOf(WowMemberResponse(WowCharacterResponse(member.name, 90))),
                            WowGuildResponse(index + 1L)
                        ).toGuildRoster()
                    )
                )
                `when`(blizzardClient.getMythicKeystoneProfile(member.region, member.realm, member.name, season.id))
                    .thenReturn(Either.Right(MythicKeystoneProfile(1500.0, emptyList())))
            }

            val errors = updater.update(
                listOf(
                    WowRealmGuild("BrokenGuild", realm, region, 1L) to brokenViewId,
                    WowRealmGuild("HealthyGuild", realm, region, 2L) to healthyViewId
                )
            )

            val failure = errors.single() as GuildUpdateFailed
            assertEquals("BrokenGuild", failure.guild.name)
            assertTrue(failure.error().contains("connection lost"))

            val healthyMemberEntity = entitiesRepository.get(healthyMember as EntityRequest, Game.WOW)!!
            assertEquals(setOf(healthyMemberEntity.id), viewsRepository.get(healthyViewId)!!.entitiesIds.toSet())
        }
    }
}
