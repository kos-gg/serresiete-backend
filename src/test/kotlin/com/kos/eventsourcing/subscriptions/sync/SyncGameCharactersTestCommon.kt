package com.kos.eventsourcing.subscriptions.sync

import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.BlizzardDatabaseClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.RetryConfig
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.LolEntity
import com.kos.entities.WowEntity
import com.kos.entities.cache.LolEntityCacheService
import com.kos.entities.cache.WowEntityCacheService
import com.kos.entities.cache.WowHardcoreEntityCacheService
import com.kos.entities.entitiesResolvers.LolResolver
import com.kos.entities.entitiesResolvers.WowHardcoreResolver
import com.kos.entities.entitiesResolvers.WowResolver
import com.kos.entities.entitiesUpdaters.LolUpdater
import com.kos.entities.entitiesUpdaters.WowHardcoreGuildUpdater
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.eventsourcing.events.*
import com.kos.views.Game
import com.kos.views.ViewsTestHelper
import com.kos.views.repository.ViewsInMemoryRepository
import io.mockk.coVerify
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.fail

abstract class SyncGameCharactersTestCommon {
    protected val retryConfig = RetryConfig(1, 1)
    protected val raiderIoClient: RaiderIoClient = mock(RaiderIoClient::class.java)
    protected val riotClient: RiotClient = mock(RiotClient::class.java)
    protected val blizzardClient: BlizzardClient = mock(BlizzardClient::class.java)
    protected val blizzardDatabaseClient: BlizzardDatabaseClient = mock(BlizzardDatabaseClient::class.java)

    protected suspend fun createService(): Pair<EntitiesService, DataCacheRepository> {
        val entitiesRepository = EntitiesInMemoryRepository().withState(
            EntitiesState(
                listOf(EntitiesTestHelper.basicWowEntity),
                listOf(EntitiesTestHelper.basicWowEntity),
                listOf(EntitiesTestHelper.basicLolEntity)
            )
        )

        val wowGuildsRepository = WowGuildsInMemoryRepository()
        val viewsRepository = ViewsInMemoryRepository()

        val wowResolver = WowResolver(entitiesRepository, raiderIoClient)
        val wowHardcoreResolver = WowHardcoreResolver(entitiesRepository, blizzardClient)
        val lolResolver = LolResolver(entitiesRepository, riotClient)

        val entitiesResolver = mapOf(
            Game.WOW to wowResolver,
            Game.WOW_HC to wowHardcoreResolver,
            Game.LOL to lolResolver
        )

        val lolUpdater = LolUpdater(riotClient, entitiesRepository)
        val wowHardcoreGuildUpdater = WowHardcoreGuildUpdater(wowHardcoreResolver, entitiesRepository, viewsRepository)


        val dataCacheRepository = DataCacheInMemoryRepository()
        val entitiesService =
            EntitiesService(
                entitiesRepository,
                wowGuildsRepository,
                entitiesResolver,
                lolUpdater,
                wowHardcoreGuildUpdater
            )

        return Pair(entitiesService, dataCacheRepository)
    }

    protected fun createEventWithVersion(eventType: EventData, game: Game): EventWithVersion {
        val payload = when (eventType) {
            is ViewCreatedEvent -> eventType.copy(game = game)
            is ViewEditedEvent -> eventType.copy(game = game)
            is ViewPatchedEvent -> eventType.copy(game = game)
            else -> eventType
        }
        return EventWithVersion(1L, Event("/credentials/owner", ViewsTestHelper.id, payload))
    }

    protected suspend fun assertLolCacheInvocation(
        entityToVerify: LolEntity,
        eventWithVersion: EventWithVersion,
        entitiesService: EntitiesService,
        dataCacheRepository: DataCacheRepository,
        lolEntityCacheService: LolEntityCacheService,
        shouldCache: Boolean,
        expectedCacheSize: Int
    ) {
        val result = LolSyncProcessor(eventWithVersion, entitiesService, lolEntityCacheService).sync()

        result.fold(
            { fail("Expected success") },
            {
                if (shouldCache) {
                    coVerify {
                        lolEntityCacheService.cache(eq(listOf(entityToVerify)))
                    }
                } else {
                    coVerify(exactly = 0) { lolEntityCacheService.cache(any()) }
                }
            }
        )

        assertEquals(expectedCacheSize, dataCacheRepository.state().size)
    }


    protected suspend fun assertWowCacheInvocation(
        entityToVerify: WowEntity,
        eventWithVersion: EventWithVersion,
        entitiesService: EntitiesService,
        dataCacheRepository: DataCacheRepository,
        wowEntityCacheService: WowEntityCacheService,
        shouldCache: Boolean,
        expectedCacheSize: Int
    ) {
        val result = WowSyncProcessor(eventWithVersion, entitiesService, wowEntityCacheService).sync()

        result.fold(
            { fail("Expected success") },
            {
                if (shouldCache) {
                    coVerify {
                        wowEntityCacheService.cache(eq(listOf(entityToVerify)))
                    }
                } else {
                    coVerify(exactly = 0) { wowEntityCacheService.cache(any()) }
                }
            }
        )

        assertEquals(expectedCacheSize, dataCacheRepository.state().size)
    }

    protected suspend fun assertWowHardcoreCacheInvocation(
        entityToVerify: WowEntity,
        eventWithVersion: EventWithVersion,
        entitiesService: EntitiesService,
        dataCacheRepository: DataCacheRepository,
        wowHardcoreEntityCacheService: WowHardcoreEntityCacheService,
        shouldCache: Boolean,
        expectedCacheSize: Int
    ) {
        val result = WowHardcoreSyncProcessor(eventWithVersion, entitiesService, wowHardcoreEntityCacheService).sync()

        result.fold(
            { fail("Expected success") },
            {
                if (shouldCache) {
                    coVerify {
                        wowHardcoreEntityCacheService.cache(eq(listOf(entityToVerify)))
                    }
                } else {
                    coVerify(exactly = 0) { wowHardcoreEntityCacheService.cache(any()) }
                }
            }
        )

        assertEquals(expectedCacheSize, dataCacheRepository.state().size)
    }
}