package com.kos.sources.lol

import arrow.core.Either
import com.kos.entities.EntitiesTestHelper
import com.kos.eventsourcing.events.ViewCreatedEvent
import com.kos.eventsourcing.events.ViewEditedEvent
import com.kos.eventsourcing.events.ViewPatchedEvent
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.sources.SyncGameCharactersTestCommon
import com.kos.views.Game
import com.kos.views.ViewsTestHelper
import com.kos.views.ViewsTestHelper.owner
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.`when`
import kotlin.test.Test

class LolEntitySynchronizerTest : SyncGameCharactersTestCommon() {

    @Test
    fun `syncLolCharactersProcessor calls cache on VIEW_CREATED for LOL`() = runBlocking {
        `when`(riotClient.getLeagueEntriesByPUUID(EntitiesTestHelper.basicLolEntity.puuid))
            .thenReturn(Either.Right(listOf()))

        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewCreatedEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                ViewsTestHelper.owner,
                listOf(EntitiesTestHelper.basicLolEntity.id),
                true,
                Game.LOL,
                ViewsTestHelper.featured,
                null
            ),
            Game.LOL
        )

        val service = LolEntitySynchronizer(dataCacheRepository, riotClient, retryConfig)
        val spied = spyk(service)

        assertLolCacheInvocation(
            EntitiesTestHelper.basicLolEntity,
            eventWithVersion,
            charactersService,
            dataCacheRepository,
            spied,
            shouldCache = true,
            expectedCacheSize = 1
        )
    }


    @Test
    fun `syncLolCharactersProcessor does not call updateLolCharacters on VIEW_CREATED with WOW game`() =
        runBlocking {
            val (charactersService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewCreatedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    ViewsTestHelper.owner,
                    listOf(EntitiesTestHelper.basicLolEntity.id),
                    true,
                    Game.WOW,
                    ViewsTestHelper.featured,
                    null
                ), Game.WOW
            )

            val service = LolEntitySynchronizer(dataCacheRepository, riotClient, retryConfig)
            val spied = spyk(service)

            assertLolCacheInvocation(

                EntitiesTestHelper.basicLolEntity,
                eventWithVersion,
                charactersService,
                dataCacheRepository,
                spied,
                shouldCache = false,
                expectedCacheSize = 0
            )
        }

    @Test
    fun `syncLolCharactersProcessor calls updateLolCharacters on VIEW_EDITED with LOL game`() = runBlocking {
        `when`(riotClient.getLeagueEntriesByPUUID(EntitiesTestHelper.basicLolEntity.puuid))
            .thenReturn(Either.Right(listOf()))
        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewEditedEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                listOf(EntitiesTestHelper.basicLolEntity.id),
                true,
                Game.LOL,
                ViewsTestHelper.featured
            ), Game.LOL
        )

        val service = LolEntitySynchronizer(dataCacheRepository, riotClient, retryConfig)
        val spied = spyk(service)

        assertLolCacheInvocation(

            EntitiesTestHelper.basicLolEntity,
            eventWithVersion,
            charactersService,
            dataCacheRepository,
            spied,
            shouldCache = true,
            expectedCacheSize = 1
        )
    }

    @Test
    fun `syncLolCharactersProcessor does not call updateLolCharacters on VIEW_EDITED with WOW game`() =
        runBlocking {
            val (charactersService, dataCacheRepository) = createService()

            val eventWithVersion = createEventWithVersion(
                ViewEditedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    listOf(EntitiesTestHelper.basicLolEntity.id),
                    true,
                    Game.WOW,
                    ViewsTestHelper.featured
                ), Game.WOW
            )

            val service = LolEntitySynchronizer(dataCacheRepository, riotClient, retryConfig)
            val spied = spyk(service)

            assertLolCacheInvocation(

                EntitiesTestHelper.basicLolEntity,
                eventWithVersion,
                charactersService,
                dataCacheRepository,
                spied,
                shouldCache = false,
                expectedCacheSize = 0
            )
        }

    @Test
    fun `syncLolCharactersProcessor calls updateLolCharacters on VIEW_PATCHED with LOL game`() = runBlocking {
        `when`(riotClient.getLeagueEntriesByPUUID(EntitiesTestHelper.basicLolEntity.puuid))
            .thenReturn(Either.Right(listOf()))
        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewPatchedEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                listOf(EntitiesTestHelper.basicLolEntity.id),
                true,
                Game.LOL,
                ViewsTestHelper.featured
            ), Game.LOL
        )

        val service = LolEntitySynchronizer(dataCacheRepository, riotClient, retryConfig)
        val spied = spyk(service)


        assertLolCacheInvocation(

            EntitiesTestHelper.basicLolEntity,
            eventWithVersion,
            charactersService,
            dataCacheRepository,
            spied,
            shouldCache = true,
            expectedCacheSize = 1
        )
    }

    @Test
    fun `syncLolCharactersProcessor does not call updateLolCharacters on VIEW_PATCHED with WOW game`() =
        runBlocking {
            val (charactersService, dataCacheRepository) = createService()

            val eventWithVersion = createEventWithVersion(
                ViewPatchedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    listOf(EntitiesTestHelper.basicLolEntity.id),
                    true,
                    Game.WOW,
                    ViewsTestHelper.featured
                ), Game.WOW
            )

            val service = LolEntitySynchronizer(dataCacheRepository, riotClient, retryConfig)
            val spied = spyk(service)

            assertLolCacheInvocation(

                EntitiesTestHelper.basicLolEntity,
                eventWithVersion,
                charactersService,
                dataCacheRepository,
                spied,
                shouldCache = false,
                expectedCacheSize = 0
            )
        }

    @Test
    fun `should ignore not related events`() {
        runBlocking {
            val (charactersService, dataCacheRepository) = createService()

            val eventWithVersion = createEventWithVersion(
                ViewToBeCreatedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    false,
                    listOf(EntitiesTestHelper.basicWowRequest),
                    Game.LOL,
                    owner,
                    ViewsTestHelper.featured,
                    null
                ), Game.LOL
            )

            val service = LolEntitySynchronizer(dataCacheRepository, riotClient, retryConfig)
            val spied = spyk(service)


            assertLolCacheInvocation(

                EntitiesTestHelper.basicLolEntity,
                eventWithVersion,
                charactersService,
                dataCacheRepository,
                spied,
                shouldCache = false,
                expectedCacheSize = 0
            )
        }
    }
}