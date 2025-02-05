package com.kos.eventsourcing

import arrow.core.Either
import com.kos.entities.Entity
import com.kos.entities.EntitiesService
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.RaiderioWowHeadEmbeddedResponse
import com.kos.clients.domain.TalentLoadout
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.ControllerError
import com.kos.common.RetryConfig
import com.kos.datacache.BlizzardMockHelper
import com.kos.datacache.DataCacheService
import com.kos.datacache.RaiderIoMockHelper
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import com.kos.eventsourcing.events.*
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.eventsourcing.subscriptions.EventSubscription
import com.kos.views.Game
import com.kos.views.ViewsTestHelper
import com.kos.views.ViewsTestHelper.owner
import io.mockk.coVerify
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Nested
import org.mockito.Mockito
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SyncCharactersSubscriptionTest {
    private val retryConfig = RetryConfig(1, 1)
    private val raiderIoClient = Mockito.mock(RaiderIoClient::class.java)
    private val riotClient = Mockito.mock(RiotClient::class.java)
    private val blizzardClient = Mockito.mock(BlizzardClient::class.java)

    private suspend fun createService(): Triple<EntitiesService, DataCacheService, DataCacheRepository> {
        val charactersRepository = EntitiesInMemoryRepository().withState(
            EntitiesState(
                listOf(EntitiesTestHelper.basicWowEntity),
                listOf(EntitiesTestHelper.basicWowEntity),
                listOf(EntitiesTestHelper.basicLolEntity)
            )
        )
        val eventStore = EventStoreInMemory()
        val dataCacheRepository = DataCacheInMemoryRepository()
        val entitiesService =
            EntitiesService(charactersRepository, raiderIoClient, riotClient, blizzardClient)
        val dataCacheService =
            DataCacheService(dataCacheRepository, charactersRepository, raiderIoClient, riotClient, blizzardClient, retryConfig, eventStore)
        return Triple(entitiesService, spyk(dataCacheService), dataCacheRepository)
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

    private suspend fun assertCacheInvocation(
        processor: suspend (EventWithVersion, EntitiesService, DataCacheService) -> Either<ControllerError, Unit>,
        gameToVerify: Game,
        entityToVerify: Entity,
        eventWithVersion: EventWithVersion,
        entitiesService: EntitiesService,
        dataCacheService: DataCacheService,
        dataCacheRepository: DataCacheRepository,
        shouldCache: Boolean,
        expectedCacheSize: Int
    ) {
        val result =
            processor(eventWithVersion, entitiesService, dataCacheService)
        result.fold(
            { fail("Expected success") },
            {
                if (shouldCache) coVerify {
                    dataCacheService.cache(
                        eq(listOf(entityToVerify)), eq(
                            gameToVerify
                        )
                    )
                }
                else coVerify(exactly = 0) { dataCacheService.cache(any(), any()) }
            }
        )
        assertEquals(expectedCacheSize, dataCacheRepository.state().size)
    }

    @Nested
    inner class BehaviorOfSyncLolProcessor {

        @Test
        fun `syncLolCharactersProcessor calls updateLolCharacters on VIEW_CREATED with LOL game`() = runBlocking {
            Mockito.`when`(riotClient.getLeagueEntriesBySummonerId(EntitiesTestHelper.basicLolEntity.summonerId))
                .thenReturn(Either.Right(listOf()))

            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewCreatedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    ViewsTestHelper.owner,
                    listOf(EntitiesTestHelper.basicLolEntity.id),
                    true,
                    Game.LOL,
                    ViewsTestHelper.featured
                ), Game.LOL
            )

            assertCacheInvocation(
                EventSubscription::syncLolEntitiesProcessor,
                Game.LOL,
                EntitiesTestHelper.basicLolEntity,
                eventWithVersion,
                charactersService,
                spiedService,
                dataCacheRepository,
                true,
                1
            )
        }

        @Test
        fun `syncLolCharactersProcessor does not call updateLolCharacters on VIEW_CREATED with WOW game`() =
            runBlocking {
                val (charactersService, spiedService, dataCacheRepository) = createService()
                val eventWithVersion = createEventWithVersion(
                    ViewCreatedEvent(
                        ViewsTestHelper.id,
                        ViewsTestHelper.name,
                        ViewsTestHelper.owner,
                        listOf(EntitiesTestHelper.basicLolEntity.id),
                        true,
                        Game.WOW,
                        ViewsTestHelper.featured
                    ), Game.WOW
                )

                assertCacheInvocation(
                    EventSubscription::syncLolEntitiesProcessor,
                    Game.LOL,
                    EntitiesTestHelper.basicLolEntity,
                    eventWithVersion,
                    charactersService,
                    spiedService,
                    dataCacheRepository,
                    false,
                    0
                )
            }

        @Test
        fun `syncLolCharactersProcessor calls updateLolCharacters on VIEW_EDITED with LOL game`() = runBlocking {
            Mockito.`when`(riotClient.getLeagueEntriesBySummonerId(EntitiesTestHelper.basicLolEntity.summonerId))
                .thenReturn(Either.Right(listOf()))
            val (charactersService, spiedService, dataCacheRepository) = createService()
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

            assertCacheInvocation(
                EventSubscription::syncLolEntitiesProcessor,
                Game.LOL,
                EntitiesTestHelper.basicLolEntity,
                eventWithVersion,
                charactersService,
                spiedService,
                dataCacheRepository,
                true,
                1
            )
        }

        @Test
        fun `syncLolCharactersProcessor does not call updateLolCharacters on VIEW_EDITED with WOW game`() =
            runBlocking {
                val (charactersService, spiedService, dataCacheRepository) = createService()
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

                assertCacheInvocation(
                    EventSubscription::syncLolEntitiesProcessor,
                    Game.LOL,
                    EntitiesTestHelper.basicLolEntity,
                    eventWithVersion,
                    charactersService,
                    spiedService,
                    dataCacheRepository,
                    false,
                    0
                )
            }

        @Test
        fun `syncLolCharactersProcessor calls updateLolCharacters on VIEW_PATCHED with LOL game`() = runBlocking {
            Mockito.`when`(riotClient.getLeagueEntriesBySummonerId(EntitiesTestHelper.basicLolEntity.summonerId))
                .thenReturn(Either.Right(listOf()))
            val (charactersService, spiedService, dataCacheRepository) = createService()
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

            assertCacheInvocation(
                EventSubscription::syncLolEntitiesProcessor,
                Game.LOL,
                EntitiesTestHelper.basicLolEntity,
                eventWithVersion,
                charactersService,
                spiedService,
                dataCacheRepository,
                true,
                1
            )
        }

        @Test
        fun `syncLolCharactersProcessor does not call updateLolCharacters on VIEW_PATCHED with WOW game`() =
            runBlocking {
                val (charactersService, spiedService, dataCacheRepository) = createService()
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

                assertCacheInvocation(
                    EventSubscription::syncLolEntitiesProcessor,
                    Game.LOL,
                    EntitiesTestHelper.basicLolEntity,
                    eventWithVersion,
                    charactersService,
                    spiedService,
                    dataCacheRepository,
                    false,
                    0
                )
            }

        @Test
        fun `should ignore not related events`() {
            runBlocking {
                val (charactersService, spiedService, dataCacheRepository) = createService()
                val eventWithVersion = createEventWithVersion(
                    ViewToBeCreatedEvent(
                        ViewsTestHelper.id,
                        ViewsTestHelper.name,
                        false,
                        listOf(EntitiesTestHelper.basicWowRequest),
                        Game.LOL,
                        owner,
                        ViewsTestHelper.featured
                    ), Game.LOL
                )

                assertCacheInvocation(
                    EventSubscription::syncLolEntitiesProcessor,
                    Game.LOL,
                    EntitiesTestHelper.basicWowEntity,
                    eventWithVersion,
                    charactersService,
                    spiedService,
                    dataCacheRepository,
                    false,
                    0
                )
            }
        }
    }

    @Nested
    inner class BehaviorOfSyncWowProcessor {

        @Test
        fun `syncWowCharactersProcessor calls cache on VIEW_CREATED with WOW game`() = runBlocking {
            Mockito.`when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
            Mockito.`when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity))
                .thenReturn(RaiderIoMockHelper.get(EntitiesTestHelper.basicWowEntity))

            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewCreatedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    ViewsTestHelper.owner,
                    listOf(EntitiesTestHelper.basicWowEntity.id),
                    true,
                    Game.WOW,
                    ViewsTestHelper.featured
                ), Game.WOW
            )

            assertCacheInvocation(
                EventSubscription::syncWowEntitiesProcessor,
                Game.WOW,
                EntitiesTestHelper.basicWowEntity,
                eventWithVersion,
                charactersService,
                spiedService,
                dataCacheRepository,
                true,
                1
            )
        }

        @Test
        fun `syncWowCharactersProcessor calls cache on VIEW_EDITED with WOW game`() = runBlocking {
            Mockito.`when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
            Mockito.`when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity))
                .thenReturn(RaiderIoMockHelper.get(EntitiesTestHelper.basicWowEntity))
            val (charactersService, spiedService, dataCacheRepository) = createService()
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

            assertCacheInvocation(
                EventSubscription::syncWowEntitiesProcessor,
                Game.WOW,
                EntitiesTestHelper.basicWowEntity,
                eventWithVersion,
                charactersService,
                spiedService,
                dataCacheRepository,
                true,
                1
            )
        }

        @Test
        fun `syncWowCharactersProcessor calls cache on VIEW_PATCHED with WOW game`() = runBlocking {
            Mockito.`when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
            Mockito.`when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity))
                .thenReturn(RaiderIoMockHelper.get(EntitiesTestHelper.basicWowEntity))
            val (charactersService, spiedService, dataCacheRepository) = createService()
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

            assertCacheInvocation(
                EventSubscription::syncWowEntitiesProcessor,
                Game.WOW,
                EntitiesTestHelper.basicWowEntity,
                eventWithVersion,
                charactersService,
                spiedService,
                dataCacheRepository,
                true,
                1
            )
        }

        @Test
        fun `should ignore not related events`() {
            runBlocking {
                val (charactersService, spiedService, dataCacheRepository) = createService()
                val eventWithVersion = createEventWithVersion(
                    ViewToBeCreatedEvent(
                        ViewsTestHelper.id,
                        ViewsTestHelper.name,
                        false,
                        listOf(EntitiesTestHelper.basicWowRequest),
                        Game.WOW,
                        owner,
                        ViewsTestHelper.featured
                    ), Game.WOW
                )

                assertCacheInvocation(
                    EventSubscription::syncWowEntitiesProcessor,
                    Game.WOW,
                    EntitiesTestHelper.basicWowEntity,
                    eventWithVersion,
                    charactersService,
                    spiedService,
                    dataCacheRepository,
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
            Mockito.`when`(
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
            Mockito.`when`(
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
            Mockito.`when`(
                blizzardClient.getCharacterEquipment(
                    EntitiesTestHelper.basicWowEntity.region,
                    EntitiesTestHelper.basicWowEntity.realm,
                    EntitiesTestHelper.basicWowEntity.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterEquipment())

            Mockito.`when`(
                blizzardClient.getCharacterStats(
                    EntitiesTestHelper.basicWowEntity.region,
                    EntitiesTestHelper.basicWowEntity.realm,
                    EntitiesTestHelper.basicWowEntity.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterStats())

            Mockito.`when`(
                blizzardClient.getCharacterSpecializations(
                    EntitiesTestHelper.basicWowEntity.region,
                    EntitiesTestHelper.basicWowEntity.realm,
                    EntitiesTestHelper.basicWowEntity.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterSpecializations())

            Mockito.`when`(
                blizzardClient.getItemMedia(
                    EntitiesTestHelper.basicWowEntity.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getItemMedia())

            Mockito.`when`(
                blizzardClient.getItem(
                    EntitiesTestHelper.basicWowEntity.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getWowItemResponse())

            Mockito.`when`(
                raiderIoClient.wowheadEmbeddedCalculator(EntitiesTestHelper.basicWowEntity)
            ).thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))

            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewCreatedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    ViewsTestHelper.owner,
                    listOf(EntitiesTestHelper.basicWowEntity.id),
                    true,
                    Game.WOW_HC,
                    ViewsTestHelper.featured
                ), Game.WOW_HC
            )

            assertCacheInvocation(
                EventSubscription::syncWowHardcoreEntitiesProcessor,
                Game.WOW_HC,
                EntitiesTestHelper.basicWowEntity,
                eventWithVersion,
                charactersService,
                spiedService,
                dataCacheRepository,
                true,
                1
            )
        }

        @Test
        fun `syncWowHcCharactersProcessor calls cache on VIEW_EDITED with WOW game`() = runBlocking {
            Mockito.`when`(
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
            Mockito.`when`(
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
            Mockito.`when`(
                blizzardClient.getCharacterEquipment(
                    EntitiesTestHelper.basicWowEntity.region,
                    EntitiesTestHelper.basicWowEntity.realm,
                    EntitiesTestHelper.basicWowEntity.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterEquipment())

            Mockito.`when`(
                blizzardClient.getCharacterStats(
                    EntitiesTestHelper.basicWowEntity.region,
                    EntitiesTestHelper.basicWowEntity.realm,
                    EntitiesTestHelper.basicWowEntity.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterStats())

            Mockito.`when`(
                blizzardClient.getCharacterSpecializations(
                    EntitiesTestHelper.basicWowEntity.region,
                    EntitiesTestHelper.basicWowEntity.realm,
                    EntitiesTestHelper.basicWowEntity.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterSpecializations())

            Mockito.`when`(
                blizzardClient.getItemMedia(
                    EntitiesTestHelper.basicWowEntity.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getItemMedia())

            Mockito.`when`(
                blizzardClient.getItem(
                    EntitiesTestHelper.basicWowEntity.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getWowItemResponse())

            Mockito.`when`(
                raiderIoClient.wowheadEmbeddedCalculator(EntitiesTestHelper.basicWowEntity)
            ).thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))

            val (charactersService, spiedService, dataCacheRepository) = createService()
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

            assertCacheInvocation(
                EventSubscription::syncWowHardcoreEntitiesProcessor,
                Game.WOW_HC,
                EntitiesTestHelper.basicWowEntity,
                eventWithVersion,
                charactersService,
                spiedService,
                dataCacheRepository,
                true,
                1
            )
        }

        @Test
        fun `syncWowHcCharactersProcessor calls cache on VIEW_PATCHED with WOW game`() = runBlocking {
            Mockito.`when`(
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
            Mockito.`when`(
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
            Mockito.`when`(
                blizzardClient.getCharacterEquipment(
                    EntitiesTestHelper.basicWowEntity.region,
                    EntitiesTestHelper.basicWowEntity.realm,
                    EntitiesTestHelper.basicWowEntity.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterEquipment())

            Mockito.`when`(
                blizzardClient.getCharacterStats(
                    EntitiesTestHelper.basicWowEntity.region,
                    EntitiesTestHelper.basicWowEntity.realm,
                    EntitiesTestHelper.basicWowEntity.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterStats())

            Mockito.`when`(
                blizzardClient.getCharacterSpecializations(
                    EntitiesTestHelper.basicWowEntity.region,
                    EntitiesTestHelper.basicWowEntity.realm,
                    EntitiesTestHelper.basicWowEntity.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterSpecializations())

            Mockito.`when`(
                blizzardClient.getItemMedia(
                    EntitiesTestHelper.basicWowEntity.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getItemMedia())

            Mockito.`when`(
                blizzardClient.getItem(
                    EntitiesTestHelper.basicWowEntity.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getWowItemResponse())

            Mockito.`when`(
                raiderIoClient.wowheadEmbeddedCalculator(EntitiesTestHelper.basicWowEntity)
            ).thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))

            val (charactersService, spiedService, dataCacheRepository) = createService()
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

            assertCacheInvocation(
                EventSubscription::syncWowHardcoreEntitiesProcessor,
                Game.WOW_HC,
                EntitiesTestHelper.basicWowEntity,
                eventWithVersion,
                charactersService,
                spiedService,
                dataCacheRepository,
                true,
                1
            )
        }

        @Test
        fun `should ignore not related events`() {
            runBlocking {
                val (charactersService, spiedService, dataCacheRepository) = createService()
                val eventWithVersion = createEventWithVersion(
                    ViewToBeCreatedEvent(
                        ViewsTestHelper.id,
                        ViewsTestHelper.name,
                        false,
                        listOf(EntitiesTestHelper.basicWowRequest),
                        Game.WOW_HC,
                        owner,
                        ViewsTestHelper.featured
                    ), Game.WOW_HC
                )

                assertCacheInvocation(
                    EventSubscription::syncWowHardcoreEntitiesProcessor,
                    Game.WOW_HC,
                    EntitiesTestHelper.basicWowEntity,
                    eventWithVersion,
                    charactersService,
                    spiedService,
                    dataCacheRepository,
                    false,
                    0
                )
            }
        }
    }
}