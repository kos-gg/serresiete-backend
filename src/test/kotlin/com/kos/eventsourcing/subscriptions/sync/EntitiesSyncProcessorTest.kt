package com.kos.eventsourcing.subscriptions.sync

import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.entities.EntitiesService
import com.kos.entities.EntityResolverProvider
import com.kos.entities.domain.WowEntity
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.eventsourcing.events.Event
import com.kos.eventsourcing.events.EventWithVersion
import com.kos.eventsourcing.events.ViewDeletedEvent
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.views.Game
import com.kos.views.SimpleView
import com.kos.views.ViewEntity
import com.kos.views.ViewsTestHelper
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito
import kotlin.test.Test

class EntitiesSyncProcessorTest {
    private val raiderIoClient = Mockito.mock(RaiderIoClient::class.java)
    private val riotClient = Mockito.mock(RiotClient::class.java)
    private val blizzardClient = Mockito.mock(BlizzardClient::class.java)

    private val aggregateRoot = "/credentials/owner"

    @Test
    fun `charactersProcessor deletes characters that are not present on any view when a view has been deleted`() {
        runBlocking {
            val charactersFromDeletedView: List<Long> = listOf(1, 2, 3)
            val expectedRemainingCharacters: List<Long> = listOf(1, 4, 5)
            val wowHardcoreRemainingView =
                SimpleView("1", "wowHcView", "owner", true, expectedRemainingCharacters, Game.WOW_HC, true)
            val viewsRepository = ViewsInMemoryRepository().withState(
                ViewsState(
                    listOf(wowHardcoreRemainingView),
                    wowHardcoreRemainingView.entitiesIds.map {
                        ViewEntity(
                            it,
                            wowHardcoreRemainingView.id,
                            "alias"
                        )
                    })
            )
            val wowHardcoreCharacters =
                (1..5).map { WowEntity(it.toLong(), it.toString(), it.toString(), it.toString(), it.toLong()) }
            val entitiesRepository = EntitiesInMemoryRepository(viewsRepository = viewsRepository).withState(
                EntitiesState(
                    wowEntities = listOf(),
                    lolEntities = listOf(),
                    wowHardcoreEntities = wowHardcoreCharacters
                )
            )

            val wowGuildsRepository = WowGuildsInMemoryRepository()

            val wowResolver = WowEntityResolver(entitiesRepository, raiderIoClient)
            val wowHardcoreResolver = WowHardcoreEntityResolver(entitiesRepository, blizzardClient)
            val lolResolver = LolEntityResolver(entitiesRepository, riotClient)

            val lolUpdater = LolEntityUpdater(riotClient, entitiesRepository)
            val wowHardcoreGuildUpdater =
                WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository)

            val entitiesResolver = EntityResolverProvider(
                listOf(
                    wowResolver,
                    wowHardcoreResolver,
                    lolResolver
                )
            )


            val service = EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )

            val eventData =
                ViewDeletedEvent(
                    "deleted",
                    "name",
                    "owner",
                    charactersFromDeletedView,
                    true,
                    Game.WOW_HC,
                    false
                )

            val eventWithVersion = EventWithVersion(
                1L,
                Event(aggregateRoot, ViewsTestHelper.id, eventData)
            )

            EntitiesEventProcessor(eventWithVersion, service).process()
            kotlin.test.assertEquals(
                expectedRemainingCharacters,
                entitiesRepository.state().wowHardcoreEntities.map { it.id })

        }
    }
}