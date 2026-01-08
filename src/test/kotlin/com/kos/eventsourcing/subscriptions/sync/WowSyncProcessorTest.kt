package com.kos.eventsourcing.subscriptions.sync

import com.kos.datacache.RaiderIoMockHelper
import com.kos.entities.EntitiesTestHelper
import com.kos.eventsourcing.events.ViewCreatedEvent
import com.kos.eventsourcing.events.ViewEditedEvent
import com.kos.eventsourcing.events.ViewPatchedEvent
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.sources.wow.WowEntitySynchronizer
import com.kos.views.Game
import com.kos.views.ViewsTestHelper
import com.kos.views.ViewsTestHelper.owner
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.`when`
import kotlin.test.Test

class WowSyncProcessorTest : SyncGameCharactersTestCommon() {
    @Test
    fun `syncWowCharactersProcessor calls cache on VIEW_CREATED with WOW game`() = runBlocking {
        `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
        `when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity))
            .thenReturn(RaiderIoMockHelper.get(EntitiesTestHelper.basicWowEntity))

        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewCreatedEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                owner,
                listOf(EntitiesTestHelper.basicWowEntity.id),
                true,
                Game.WOW,
                ViewsTestHelper.featured,
                null
            ), Game.WOW
        )

        val service = WowEntitySynchronizer(dataCacheRepository, raiderIoClient, retryConfig)
        val spied = spyk(service)

        assertWowCacheInvocation(

            EntitiesTestHelper.basicWowEntity,
            eventWithVersion,
            charactersService,
            dataCacheRepository,
            spied,
            true,
            1
        )
    }

    @Test
    fun `syncWowCharactersProcessor calls cache on VIEW_EDITED with WOW game`() = runBlocking {
        `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
        `when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity))
            .thenReturn(RaiderIoMockHelper.get(EntitiesTestHelper.basicWowEntity))
        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewEditedEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                listOf(EntitiesTestHelper.basicWowEntity.id),
                true,
                Game.WOW,
                ViewsTestHelper.featured
            ), Game.WOW
        )

        val service = WowEntitySynchronizer(dataCacheRepository, raiderIoClient, retryConfig)
        val spied = spyk(service)

        assertWowCacheInvocation(

            EntitiesTestHelper.basicWowEntity,
            eventWithVersion,
            charactersService,
            dataCacheRepository,
            spied,
            true,
            1
        )

    }

    @Test
    fun `syncWowCharactersProcessor calls cache on VIEW_PATCHED with WOW game`() = runBlocking {
        `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
        `when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity))
            .thenReturn(RaiderIoMockHelper.get(EntitiesTestHelper.basicWowEntity))
        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewPatchedEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                listOf(EntitiesTestHelper.basicWowEntity.id),
                true,
                Game.WOW,
                ViewsTestHelper.featured
            ), Game.WOW
        )

        val service = WowEntitySynchronizer(dataCacheRepository, raiderIoClient, retryConfig)
        val spied = spyk(service)

        assertWowCacheInvocation(

            EntitiesTestHelper.basicWowEntity,
            eventWithVersion,
            charactersService,
            dataCacheRepository,
            spied,
            true,
            1
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
                    Game.WOW,
                    owner,
                    ViewsTestHelper.featured,
                    null
                ), Game.WOW
            )

            val service = WowEntitySynchronizer(dataCacheRepository, raiderIoClient, retryConfig)
            val spied = spyk(service)

            assertWowCacheInvocation(

                EntitiesTestHelper.basicWowEntity,
                eventWithVersion,
                charactersService,
                dataCacheRepository,
                spied,
                false,
                0
            )
        }
    }
}