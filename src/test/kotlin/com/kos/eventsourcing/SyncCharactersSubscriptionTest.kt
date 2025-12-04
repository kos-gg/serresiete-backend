package com.kos.eventsourcing

import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.BlizzardDatabaseClient
import com.kos.clients.domain.RaiderioWowHeadEmbeddedResponse
import com.kos.clients.domain.TalentLoadout
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.ControllerError
import com.kos.common.RetryConfig
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.RaiderIoMockHelper
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
import com.kos.eventsourcing.subscriptions.EventSubscription
import com.kos.views.Game
import com.kos.views.ViewsTestHelper
import com.kos.views.ViewsTestHelper.owner
import com.kos.views.repository.ViewsInMemoryRepository
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SyncCharactersSubscriptionTest {
    private val retryConfig = RetryConfig(1, 1)
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val blizzardDatabaseClient = mock(BlizzardDatabaseClient::class.java)

    @Nested
    inner class BehaviorOfSyncLolProcessor {

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

            val service = LolEntityCacheService(dataCacheRepository, riotClient, retryConfig)
            val spied = spyk(service)

            assertLolCacheInvocation(
                EventSubscription::syncLolEntitiesProcessor,
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

                val service = LolEntityCacheService(dataCacheRepository, riotClient, retryConfig)
                val spied = spyk(service)

                assertLolCacheInvocation(
                    EventSubscription::syncLolEntitiesProcessor,
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

            val service = LolEntityCacheService(dataCacheRepository, riotClient, retryConfig)
            val spied = spyk(service)

            assertLolCacheInvocation(
                EventSubscription::syncLolEntitiesProcessor,
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

                val service = LolEntityCacheService(dataCacheRepository, riotClient, retryConfig)
                val spied = spyk(service)

                assertLolCacheInvocation(
                    EventSubscription::syncLolEntitiesProcessor,
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

            val service = LolEntityCacheService(dataCacheRepository, riotClient, retryConfig)
            val spied = spyk(service)


            assertLolCacheInvocation(
                EventSubscription::syncLolEntitiesProcessor,
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

                val service = LolEntityCacheService(dataCacheRepository, riotClient, retryConfig)
                val spied = spyk(service)

                assertLolCacheInvocation(
                    EventSubscription::syncLolEntitiesProcessor,
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

                val service = LolEntityCacheService(dataCacheRepository, riotClient, retryConfig)
                val spied = spyk(service)


                assertLolCacheInvocation(
                    EventSubscription::syncLolEntitiesProcessor,
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

    @Nested
    inner class BehaviorOfSyncWowProcessor {

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
                    ViewsTestHelper.owner,
                    listOf(EntitiesTestHelper.basicWowEntity.id),
                    true,
                    Game.WOW,
                    ViewsTestHelper.featured,
                    null
                ), Game.WOW
            )

            val service = WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
            val spied = spyk(service)

            assertWowCacheInvocation(
                EventSubscription::syncWowEntitiesProcessor,
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

            val service = WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
            val spied = spyk(service)

            assertWowCacheInvocation(
                EventSubscription::syncWowEntitiesProcessor,
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

            val service = WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
            val spied = spyk(service)

            assertWowCacheInvocation(
                EventSubscription::syncWowEntitiesProcessor,
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

                val service = WowEntityCacheService(dataCacheRepository, raiderIoClient, retryConfig)
                val spied = spyk(service)

                assertWowCacheInvocation(
                    EventSubscription::syncWowEntitiesProcessor,
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

    @Nested
    inner class BehaviorOfSyncWowHardcoreProcessor {
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

            val wowHardcoreEntityCacheService = WowHardcoreEntityCacheService(
                dataCacheRepository,
                entitiesRepository = EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                blizzardDatabaseClient,
                retryConfig
            )
            val spied = spyk(wowHardcoreEntityCacheService)
            assertWowHardcoreCacheInvocation(
                EventSubscription::syncWowHardcoreEntitiesProcessor,
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

            val wowHardcoreEntityCacheService = WowHardcoreEntityCacheService(
                dataCacheRepository,
                entitiesRepository = EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                blizzardDatabaseClient,
                retryConfig
            )
            val spied = spyk(wowHardcoreEntityCacheService)
            assertWowHardcoreCacheInvocation(
                EventSubscription::syncWowHardcoreEntitiesProcessor,
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


            val wowHardcoreEntityCacheService = WowHardcoreEntityCacheService(
                dataCacheRepository,
                entitiesRepository = EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                blizzardDatabaseClient,
                retryConfig
            )
            val spied = spyk(wowHardcoreEntityCacheService)
            assertWowHardcoreCacheInvocation(
                EventSubscription::syncWowHardcoreEntitiesProcessor,
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


                val wowHardcoreEntityCacheService = WowHardcoreEntityCacheService(
                    dataCacheRepository,
                    entitiesRepository = EntitiesInMemoryRepository(),
                    raiderIoClient,
                    blizzardClient,
                    blizzardDatabaseClient,
                    retryConfig
                )
                val spied = spyk(wowHardcoreEntityCacheService)
                assertWowHardcoreCacheInvocation(
                    EventSubscription::syncWowHardcoreEntitiesProcessor,
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

    private suspend fun createService(): Pair<EntitiesService, DataCacheRepository> {
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

    private fun createEventWithVersion(eventType: EventData, game: Game): EventWithVersion {
        val payload = when (eventType) {
            is ViewCreatedEvent -> eventType.copy(game = game)
            is ViewEditedEvent -> eventType.copy(game = game)
            is ViewPatchedEvent -> eventType.copy(game = game)
            else -> eventType
        }
        return EventWithVersion(1L, Event("/credentials/owner", ViewsTestHelper.id, payload))
    }

    private suspend fun assertLolCacheInvocation(
        processor: suspend (
            EventWithVersion,
            EntitiesService,
            LolEntityCacheService
        ) -> Either<ControllerError, Unit>,
        entityToVerify: LolEntity,
        eventWithVersion: EventWithVersion,
        entitiesService: EntitiesService,
        dataCacheRepository: DataCacheRepository,
        lolEntityCacheService: LolEntityCacheService,
        shouldCache: Boolean,
        expectedCacheSize: Int
    ) {
        val result = processor(eventWithVersion, entitiesService, lolEntityCacheService)

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


    private suspend fun assertWowCacheInvocation(
        processor: suspend (
            EventWithVersion,
            EntitiesService,
            WowEntityCacheService
        ) -> Either<ControllerError, Unit>,
        entityToVerify: WowEntity,
        eventWithVersion: EventWithVersion,
        entitiesService: EntitiesService,
        dataCacheRepository: DataCacheRepository,
        wowEntityCacheService: WowEntityCacheService,
        shouldCache: Boolean,
        expectedCacheSize: Int
    ) {
        val result = processor(eventWithVersion, entitiesService, wowEntityCacheService)

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

    private suspend fun assertWowHardcoreCacheInvocation(
        processor: suspend (
            EventWithVersion,
            EntitiesService,
            WowHardcoreEntityCacheService
        ) -> Either<ControllerError, Unit>,
        entityToVerify: WowEntity,
        eventWithVersion: EventWithVersion,
        entitiesService: EntitiesService,
        dataCacheRepository: DataCacheRepository,
        wowHardcoreEntityCacheService: WowHardcoreEntityCacheService,
        shouldCache: Boolean,
        expectedCacheSize: Int
    ) {
        val result = processor(eventWithVersion, entitiesService, wowHardcoreEntityCacheService)

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