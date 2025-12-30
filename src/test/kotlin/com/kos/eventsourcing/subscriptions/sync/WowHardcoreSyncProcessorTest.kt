package com.kos.eventsourcing.subscriptions.sync

import arrow.core.Either
import com.kos.clients.domain.RaiderioWowHeadEmbeddedResponse
import com.kos.clients.domain.TalentLoadout
import com.kos.datacache.BlizzardMockHelper
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.eventsourcing.events.ViewCreatedEvent
import com.kos.eventsourcing.events.ViewEditedEvent
import com.kos.eventsourcing.events.ViewPatchedEvent
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.sources.wowhc.WowHardcoreEntitySynchronizer
import com.kos.views.Game
import com.kos.views.ViewsTestHelper
import com.kos.views.ViewsTestHelper.owner
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.mockito.Mockito.`when`
import kotlin.test.Test

class WowHardcoreSyncProcessorTest : SyncGameCharactersTestCommon() {
    @Test
    fun `syncWowHcCharactersProcessor calls cache on VIEW_CREATED with WOW_HC game`() = runBlocking {
        `when`(
            blizzardClient.getCharacterProfile(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(
            BlizzardMockHelper.getCharacterProfile(
                EntitiesTestHelper.basicWowEntity
            )
        )
        `when`(
            blizzardClient.getCharacterMedia(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(
            BlizzardMockHelper.getCharacterMedia(
                EntitiesTestHelper.basicWowEntity
            )
        )
        `when`(
            blizzardClient.getCharacterEquipment(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(BlizzardMockHelper.getCharacterEquipment())

        `when`(
            blizzardClient.getCharacterStats(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(BlizzardMockHelper.getCharacterStats())

        `when`(
            blizzardClient.getCharacterSpecializations(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(BlizzardMockHelper.getCharacterSpecializations())

        `when`(
            blizzardClient.getItemMedia(
                EntitiesTestHelper.basicWowEntity.region,
                18421
            )
        ).thenReturn(BlizzardMockHelper.getItemMedia())

        `when`(
            blizzardClient.getItem(
                EntitiesTestHelper.basicWowEntity.region,
                18421
            )
        ).thenReturn(BlizzardMockHelper.getWowItemResponse())

        `when`(
            raiderIoClient.wowheadEmbeddedCalculator(EntitiesTestHelper.basicWowEntity)
        ).thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))

        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewCreatedEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                ViewsTestHelper.owner,
                listOf(EntitiesTestHelper.basicWowEntity.id),
                true,
                Game.WOW_HC,
                ViewsTestHelper.featured,
                null
            ), Game.WOW_HC
        )

        val wowHardcoreEntityCacheService = WowHardcoreEntitySynchronizer(
            dataCacheRepository,
            entitiesRepository = EntitiesInMemoryRepository(),
            raiderIoClient,
            blizzardClient,
            blizzardDatabaseClient,
            retryConfig
        )
        val spied = spyk(wowHardcoreEntityCacheService)
        assertWowHardcoreCacheInvocation(

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
    fun `syncWowHcCharactersProcessor calls cache on VIEW_EDITED with WOW game`() = runBlocking {
        `when`(
            blizzardClient.getCharacterProfile(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(
            BlizzardMockHelper.getCharacterProfile(
                EntitiesTestHelper.basicWowEntity
            )
        )
        `when`(
            blizzardClient.getCharacterMedia(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(
            BlizzardMockHelper.getCharacterMedia(
                EntitiesTestHelper.basicWowEntity
            )
        )
        `when`(
            blizzardClient.getCharacterEquipment(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(BlizzardMockHelper.getCharacterEquipment())

        `when`(
            blizzardClient.getCharacterStats(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(BlizzardMockHelper.getCharacterStats())

        `when`(
            blizzardClient.getCharacterSpecializations(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(BlizzardMockHelper.getCharacterSpecializations())

        `when`(
            blizzardClient.getItemMedia(
                EntitiesTestHelper.basicWowEntity.region,
                18421
            )
        ).thenReturn(BlizzardMockHelper.getItemMedia())

        `when`(
            blizzardClient.getItem(
                EntitiesTestHelper.basicWowEntity.region,
                18421
            )
        ).thenReturn(BlizzardMockHelper.getWowItemResponse())

        `when`(
            raiderIoClient.wowheadEmbeddedCalculator(EntitiesTestHelper.basicWowEntity)
        ).thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))

        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewEditedEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                listOf(EntitiesTestHelper.basicWowEntity.id),
                true,
                Game.WOW_HC,
                ViewsTestHelper.featured
            ), Game.WOW_HC
        )

        val wowHardcoreEntityCacheService = WowHardcoreEntitySynchronizer(
            dataCacheRepository,
            entitiesRepository = EntitiesInMemoryRepository(),
            raiderIoClient,
            blizzardClient,
            blizzardDatabaseClient,
            retryConfig
        )
        val spied = spyk(wowHardcoreEntityCacheService)
        assertWowHardcoreCacheInvocation(

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
    fun `syncWowHcCharactersProcessor calls cache on VIEW_PATCHED with WOW game`() = runBlocking {
        `when`(
            blizzardClient.getCharacterProfile(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(
            BlizzardMockHelper.getCharacterProfile(
                EntitiesTestHelper.basicWowEntity
            )
        )
        `when`(
            blizzardClient.getCharacterMedia(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(
            BlizzardMockHelper.getCharacterMedia(
                EntitiesTestHelper.basicWowEntity
            )
        )
        `when`(
            blizzardClient.getCharacterEquipment(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(BlizzardMockHelper.getCharacterEquipment())

        `when`(
            blizzardClient.getCharacterStats(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(BlizzardMockHelper.getCharacterStats())

        `when`(
            blizzardClient.getCharacterSpecializations(
                EntitiesTestHelper.basicWowEntity.region,
                EntitiesTestHelper.basicWowEntity.realm,
                EntitiesTestHelper.basicWowEntity.name
            )
        ).thenReturn(BlizzardMockHelper.getCharacterSpecializations())

        `when`(
            blizzardClient.getItemMedia(
                EntitiesTestHelper.basicWowEntity.region,
                18421
            )
        ).thenReturn(BlizzardMockHelper.getItemMedia())

        `when`(
            blizzardClient.getItem(
                EntitiesTestHelper.basicWowEntity.region,
                18421
            )
        ).thenReturn(BlizzardMockHelper.getWowItemResponse())

        `when`(
            raiderIoClient.wowheadEmbeddedCalculator(EntitiesTestHelper.basicWowEntity)
        ).thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))

        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewPatchedEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                listOf(EntitiesTestHelper.basicWowEntity.id),
                true,
                Game.WOW_HC,
                ViewsTestHelper.featured
            ), Game.WOW_HC
        )


        val wowHardcoreEntityCacheService = WowHardcoreEntitySynchronizer(
            dataCacheRepository,
            entitiesRepository = EntitiesInMemoryRepository(),
            raiderIoClient,
            blizzardClient,
            blizzardDatabaseClient,
            retryConfig
        )
        val spied = spyk(wowHardcoreEntityCacheService)
        assertWowHardcoreCacheInvocation(

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
                    Game.WOW_HC,
                    owner,
                    ViewsTestHelper.featured,
                    null
                ), Game.WOW_HC
            )


            val wowHardcoreEntityCacheService = WowHardcoreEntitySynchronizer(
                dataCacheRepository,
                entitiesRepository = EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                blizzardDatabaseClient,
                retryConfig
            )
            val spied = spyk(wowHardcoreEntityCacheService)
            assertWowHardcoreCacheInvocation(

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