package com.kos.views

import com.kos.entities.domain.WowGuild
import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.datacache.BlizzardMockHelper
import com.kos.entities.domain.LolSummoner
import com.kos.clients.riot.RiotClient
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicLolEntity2
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowEntity2
import com.kos.entities.EntitiesTestHelper.emptyEntitiesState
import com.kos.entities.EntityResolverProvider
import com.kos.entities.domain.WowRealmGuild
import com.kos.entities.domain.LolEntityRequest
import com.kos.entities.domain.WowEntityRequest
import com.kos.entities.domain.WowForeverEntity
import com.kos.entities.domain.WowForeverEntityRequest
import com.kos.entities.domain.WowForeverGuildMember
import com.kos.entities.domain.WowForeverGuildRoster
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.entities.repository.wowguilds.WowGuildsState
import com.kos.eventsourcing.events.*
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wow.WowGuildUpdater
import com.kos.sources.wowforever.WowForeverGuildResolver
import com.kos.sources.wowforever.WowForeverGuildUpdater
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.ViewsTestHelper.basicSimpleLolView
import com.kos.views.ViewsTestHelper.basicSimpleWowView
import com.kos.views.ViewsTestHelper.id
import com.kos.views.ViewsTestHelper.name
import com.kos.views.ViewsTestHelper.owner
import com.kos.views.ViewsTestHelper.published
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import io.mockk.InternalPlatformDsl.toStr
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Nested
import org.mockito.Mockito.*
import org.mockito.kotlin.any
import org.mockito.kotlin.never
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import java.util.*
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail
import com.kos.sources.wow.WowGuildResolver
import com.kos.sources.wowhc.WowHardcoreGuildResolver
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository

class ViewsEventServiceTest {
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)

    @Nested
    inner class BehaviorOfCreateView {

        @Test
        fun `create view processing view to be created event stores an event`() {
            runBlocking {
                val viewsEventService = createService(
                    ViewsState(listOf(), listOf()),
                    emptyEntitiesState
                )

                assertEquals(
                    ViewCreatedEvent(id, name, owner, listOf(), published, Game.LOL, false, null),
                    viewsEventService.createView(
                        ViewToBeCreatedEvent(id, name, published, listOf(), Game.LOL, owner, false, null)
                    )
                )
            }
        }

        @Test
        fun `creating a guild view for an already-tracked guild reuses its entities without calling blizzard or raiderio`() {
            runBlocking {
                val existingViewId = "existing-guild-view"
                val guildExtraArguments = WowExtraArguments(isGuild = true)
                val guildRequest = WowEntityRequest("method", "eu", "twisting-nether")

                val existingView = SimpleView(
                    existingViewId, "Method roster", owner, false,
                    listOf(basicWowEntity.id), Game.WOW, false, guildExtraArguments
                )

                val viewsEventService = createService(
                    ViewsState(
                        listOf(existingView),
                        listOf(ViewEntity(basicWowEntity.id, existingViewId, "alias"))
                    ),
                    EntitiesState(listOf(basicWowEntity), listOf(), listOf()),
                    wowGuildsState = listOf(
                        Triple(WowRealmGuild("method", "twisting-nether", "eu", 999L), existingViewId, Game.WOW)
                    )
                )

                assertEquals(
                    ViewCreatedEvent(
                        id, "Method roster copy", owner, listOf(basicWowEntity.id), published, Game.WOW, false, guildExtraArguments
                    ),
                    viewsEventService.createView(
                        ViewToBeCreatedEvent(
                            id, "Method roster copy", published, listOf(guildRequest), Game.WOW, owner, false, guildExtraArguments
                        )
                    )
                )

                verifyNoInteractions(blizzardClient)
            }
        }

        @Test
        fun `creating a wow forever guild view tracks every roster member, reusing tracked ones by blizzard id`() {
            runBlocking {
                val guildExtraArguments = WowExtraArguments(isGuild = true)
                val tracked = WowForeverEntity(10, "oldname", "oldsurname", "eu", 1)
                `when`(blizzardClient.getForeverGuildRoster("eu", "tsalab")).thenReturn(
                    Either.Right(
                        WowForeverGuildRoster(
                            104078,
                            listOf(WowForeverGuildMember(1, "Sanxei", "Zugzug"), WowForeverGuildMember(2, "Eredeth", "Why"))
                        )
                    )
                )

                val viewsEventService = createService(
                    ViewsState(listOf(), listOf()),
                    EntitiesState(listOf(), listOf(), listOf(), listOf(tracked))
                )

                val created = viewsEventService.createView(
                    ViewToBeCreatedEvent(
                        id, "TSALAB roster", published,
                        listOf(WowForeverEntityRequest(name = "tsalab", region = "eu")),
                        Game.WOW_FOREVER, owner, false, guildExtraArguments
                    )
                )

                assertEquals(2, created.entities.size)
                assertTrue(tracked.id in created.entities)
                verify(blizzardClient, never()).getForeverProfile(any(), any(), any())
            }
        }

        @Test
        fun `creating a second view for an already-tracked wow forever guild reuses its entities without calling blizzard again`() {
            runBlocking {
                val guildExtraArguments = WowExtraArguments(isGuild = true)
                val guildRequest = WowForeverEntityRequest(name = "tsalab", region = "eu")
                `when`(blizzardClient.getForeverGuildRoster("eu", "tsalab")).thenReturn(
                    Either.Right(WowForeverGuildRoster(104078, listOf(WowForeverGuildMember(1, "Sanxei", "Zugzug"))))
                )

                val viewsEventService = createService(ViewsState(listOf(), listOf()), emptyEntitiesState)

                val first = viewsEventService.createView(
                    ViewToBeCreatedEvent(
                        "first", "TSALAB roster", published, listOf(guildRequest), Game.WOW_FOREVER, owner, false, guildExtraArguments
                    )
                )
                val second = viewsEventService.createView(
                    ViewToBeCreatedEvent(
                        "second", "TSALAB roster copy", published, listOf(guildRequest), Game.WOW_FOREVER, owner, false, guildExtraArguments
                    )
                )

                assertEquals(first.entities, second.entities)
                verify(blizzardClient, times(1)).getForeverGuildRoster("eu", "tsalab")
            }
        }
    }

    @Nested
    inner class BehaviorOfEditView {

        @Test
        fun `editing a lol view processing view to be edited stores an event`() {
            runBlocking {
                val viewsEventService = createService(
                    ViewsState(
                        listOf(basicSimpleLolView),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    emptyEntitiesState
                )

                val newName = "new-name"
                assertEquals(
                    ViewEditedEvent(id, newName, listOf(), published, Game.LOL, false),
                    viewsEventService.editView(ViewToBeEditedEvent(id, newName, published, listOf(), Game.LOL, false))
                )
            }
        }

        @Test
        fun `editing a view processing view to be edited, an event is stored with the actual characters of the view`() {
            runBlocking {
                val request1 = WowEntityRequest("a", "r", "r")
                val request2 = WowEntityRequest("b", "r", "r")
                val request3 = WowEntityRequest("c", "r", "r")
                val request4 = WowEntityRequest("d", "r", "r")

                `when`(blizzardClient.getRetailProfile(request1.region, request1.realm, request1.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request1))
                `when`(blizzardClient.getRetailProfile(request2.region, request2.realm, request2.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request2))
                `when`(blizzardClient.getRetailProfile(request3.region, request3.realm, request3.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request3))
                `when`(blizzardClient.getRetailProfile(request4.region, request4.realm, request4.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request4))

                val viewsEventService = createService(
                    ViewsState(
                        listOf(basicSimpleWowView.copy(entitiesIds = listOf(1))),
                        basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") }),
                    EntitiesState(
                        listOf(basicWowEntity, basicWowEntity2),
                        listOf(),
                        listOf()
                    )
                )

                assertEquals(
                    ViewEditedEvent(id, name, listOf(3, 4, 5, 6), published, Game.WOW, false),
                    viewsEventService.editView(
                        ViewToBeEditedEvent(
                            id,
                            name,
                            published,
                            listOf(request1, request2, request3, request4),
                            Game.WOW,
                            false
                        )
                    )
                )
            }
        }

        @Test
        fun `editing a lol view processing view to be edited, an event is stored with the actual characters of the view`() {
            runBlocking {
                val charactersRequest = (3..6).map { LolEntityRequest(it.toString(), it.toString()) }

                val viewsEventService = createService(
                    ViewsState(
                        listOf(basicSimpleLolView.copy(entitiesIds = listOf(1))),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    EntitiesState(
                        listOf(),
                        listOf(),
                        listOf(basicLolEntity, basicLolEntity2)
                    )
                )

                `when`(riotClient.getPUUIDByRiotId(anyString(), anyString())).thenAnswer { invocation ->
                    val name = invocation.getArgument<String>(0)
                    val tag = invocation.getArgument<String>(1)
                    Either.Right(UUID.randomUUID().toString())
                }

                `when`(riotClient.getSummonerByPuuid(anyString())).thenAnswer { invocation ->
                    val puuid = invocation.getArgument<String>(0)
                    Either.Right(
                        LolSummoner(puuid, 10, 200)
                    )
                }

                assertEquals(
                    ViewEditedEvent(id, name, listOf(3, 4, 5, 6), published, Game.LOL, false),
                    viewsEventService.editView(
                        ViewToBeEditedEvent(id, name, published, charactersRequest, Game.LOL, false)
                    )
                )
            }
        }
    }

    @Nested
    inner class BehaviorOfPatchView {
        @Test
        fun `patching a lol view processing event stores an event`() {
            runBlocking {
                val charactersRequest = (3..6).map { LolEntityRequest(it.toString(), it.toString()) }

                val viewsEventService = createService(
                    ViewsState(
                        listOf(basicSimpleLolView.copy(entitiesIds = listOf(1))),
                        basicSimpleLolView.entitiesIds.map { ViewEntity(it, basicSimpleLolView.id, "alias") }),
                    emptyEntitiesState
                )

                `when`(riotClient.getPUUIDByRiotId(anyString(), anyString())).thenAnswer { invocation ->
                    val name = invocation.getArgument<String>(0)
                    val tag = invocation.getArgument<String>(1)
                    Either.Right(UUID.randomUUID().toString())
                }

                `when`(riotClient.getSummonerByPuuid(anyString())).thenAnswer { invocation ->
                    val puuid = invocation.getArgument<String>(0)
                    Either.Right(
                        LolSummoner(puuid, 10, 200)
                    )
                }

                assertEquals(
                    ViewPatchedEvent(id, null, listOf(1, 2, 3, 4), null, Game.LOL, false),
                    viewsEventService.patchView(
                        ViewToBePatchedEvent(id, null, null, charactersRequest, Game.LOL, false)
                    )
                )
            }
        }

        @Test
        fun `patching a wow view processing event stores an event`() {
            runBlocking {

                val request1 = WowEntityRequest("a", "r", "r")
                val request2 = WowEntityRequest("b", "r", "r")
                val request3 = WowEntityRequest("c", "r", "r")
                val request4 = WowEntityRequest("d", "r", "r")

                `when`(blizzardClient.getRetailProfile(request1.region, request1.realm, request1.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request1))
                `when`(blizzardClient.getRetailProfile(request2.region, request2.realm, request2.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request2))
                `when`(blizzardClient.getRetailProfile(request3.region, request3.realm, request3.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request3))
                `when`(blizzardClient.getRetailProfile(request4.region, request4.realm, request4.name))
                    .thenReturn(BlizzardMockHelper.getCharacterProfile(request4))

                val viewsEventService = createService(
                    ViewsState(
                        listOf(basicSimpleWowView.copy(entitiesIds = listOf(1))),
                        basicSimpleWowView.entitiesIds.map { ViewEntity(it, basicSimpleWowView.id, "alias") }),
                    emptyEntitiesState
                )

                val charactersRequest = listOf(request1, request2, request3, request4)

                assertEquals(
                    ViewPatchedEvent(id, null, listOf(1, 2, 3, 4), null, Game.WOW, false),
                    viewsEventService.patchView(
                        ViewToBePatchedEvent(id, null, null, charactersRequest, Game.WOW, false)
                    )
                )
            }
        }
    }

    private suspend fun createService(
        viewsState: ViewsState,
        entitiesState: EntitiesState,
        wowGuildsState: List<Triple<WowGuild, String, Game>> = listOf(),
    ): ViewsEventService {
        val viewsRepository = ViewsInMemoryRepository()
            .withState(viewsState)
        val entitiesRepository = EntitiesInMemoryRepository()
            .withState(entitiesState)

        val wowGuildsRepository = WowGuildsInMemoryRepository()
            .withState(WowGuildsState(wowGuildsState))

        val wowResolver = WowEntityResolver(entitiesRepository, blizzardClient)
        val wowGuildResolver = WowGuildResolver(entitiesRepository, blizzardClient, WowSeasonInMemoryRepository())
        val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
        val wowHardcoreGuildResolver = WowHardcoreGuildResolver(blizzardClient, wowHardcoreResolver)
        val lolResolver = LolEntityResolver(entitiesRepository, riotClient)
        val wowForeverGuildResolver = WowForeverGuildResolver(entitiesRepository, blizzardClient)

        val entitiesResolver = EntityResolverProvider(
            wowResolver = wowResolver,
            wowGuildResolver = wowGuildResolver,
            wowHardcoreResolver = wowHardcoreResolver,
            wowHardcoreGuildResolver = wowHardcoreGuildResolver,
            lolResolver = lolResolver,
            wowForeverResolver = mock(),
            wowForeverGuildResolver = wowForeverGuildResolver
        )

        val lolUpdater = LolEntityUpdater(riotClient, entitiesRepository)
        val wowHardcoreGuildUpdater = WowHardcoreGuildUpdater(wowHardcoreGuildResolver, wowHardcoreResolver, entitiesRepository, viewsRepository)
        val wowGuildUpdater = WowGuildUpdater(wowGuildResolver, entitiesRepository, viewsRepository)

        val entitiesService = EntitiesService(
            entitiesRepository,
            wowGuildsRepository,
            entitiesResolver,
            wowGuildResolver,
            lolUpdater,
            wowHardcoreGuildUpdater,
            wowGuildUpdater,
            wowForeverGuildResolver,
            WowForeverGuildUpdater(wowForeverGuildResolver, entitiesRepository, viewsRepository)
        )

        return ViewsEventService(viewsRepository, entitiesService)
    }
}
