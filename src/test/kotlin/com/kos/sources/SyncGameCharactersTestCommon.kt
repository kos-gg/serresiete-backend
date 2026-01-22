package com.kos.sources

import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.EntityResolverProvider
import com.kos.entities.domain.LolEntity
import com.kos.entities.domain.WowEntity
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.entities.repository.wowguilds.WowGuildsInMemoryRepository
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.subscriptions.sync.LolEventProcessor
import com.kos.eventsourcing.subscriptions.sync.WowEventProcessor
import com.kos.eventsourcing.subscriptions.sync.WowHardcoreEventProcessor
import com.kos.sources.lol.LolEntityResolver
import com.kos.sources.lol.LolEntitySynchronizer
import com.kos.sources.lol.LolEntityUpdater
import com.kos.sources.wow.WowEntityResolver
import com.kos.sources.wow.WowEntitySynchronizer
import com.kos.sources.wowhc.WowHardcoreEntityResolver
import com.kos.sources.wowhc.WowHardcoreEntitySynchronizer
import com.kos.sources.wowhc.WowHardcoreGuildUpdater
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.views.Game
import com.kos.views.ViewsTestHelper
import com.kos.views.repository.ViewsInMemoryRepository
import io.mockk.coVerify
import org.mockito.Mockito.mock
import kotlin.test.assertEquals
import kotlin.test.fail

abstract class SyncGameCharactersTestCommon {
    protected val raiderIoClient: RaiderIoClient = mock(RaiderIoClient::class.java)
    protected val riotClient: RiotClient = mock(RiotClient::class.java)
    protected val blizzardClient: BlizzardClient = mock(BlizzardClient::class.java)
    protected val blizzardDatabaseClient: WowItemsDatabaseRepository = mock(WowItemsDatabaseRepository::class.java)

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
        lolEntitySynchronizer: LolEntitySynchronizer,
        shouldCache: Boolean,
        expectedCacheSize: Int
    ) {
        val result = LolEventProcessor(eventWithVersion, entitiesService, lolEntitySynchronizer).process()

        result.fold(
            { fail("Expected success") },
            {
                if (shouldCache) {
                    coVerify {
                        lolEntitySynchronizer.synchronize(eq(listOf(entityToVerify)))
                    }
                } else {
                    coVerify(exactly = 0) { lolEntitySynchronizer.synchronize(any()) }
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
        wowEntityCacheService: WowEntitySynchronizer,
        shouldCache: Boolean,
        expectedCacheSize: Int
    ) {
        val result = WowEventProcessor(eventWithVersion, entitiesService, wowEntityCacheService).process()

        result.fold(
            { fail("Expected success") },
            {
                if (shouldCache) {
                    coVerify {
                        wowEntityCacheService.synchronize(eq(listOf(entityToVerify)))
                    }
                } else {
                    coVerify(exactly = 0) { wowEntityCacheService.synchronize(any()) }
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
        wowHardcoreEntityCacheService: WowHardcoreEntitySynchronizer,
        shouldCache: Boolean,
        expectedCacheSize: Int
    ) {
        val result = WowHardcoreEventProcessor(eventWithVersion, entitiesService, wowHardcoreEntityCacheService).process()

        result.fold(
            { fail("Expected success") },
            {
                if (shouldCache) {
                    coVerify {
                        wowHardcoreEntityCacheService.synchronize(eq(listOf(entityToVerify)))
                    }
                } else {
                    coVerify(exactly = 0) { wowHardcoreEntityCacheService.synchronize(any()) }
                }
            }
        )

        assertEquals(expectedCacheSize, dataCacheRepository.state().size)
    }
}