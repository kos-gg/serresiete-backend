package com.kos.eventsourcing

import arrow.core.Either
import com.kos.characters.Character
import com.kos.characters.CharactersService
import com.kos.characters.CharactersTestHelper
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.characters.repository.CharactersState
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

    private suspend fun createService(): Triple<CharactersService, DataCacheService, DataCacheRepository> {
        val charactersRepository = CharactersInMemoryRepository().withState(
            CharactersState(
                listOf(CharactersTestHelper.basicWowCharacter),
                listOf(CharactersTestHelper.basicWowCharacter),
                listOf(CharactersTestHelper.basicLolCharacter)
            )
        )
        val dataCacheRepository = DataCacheInMemoryRepository()
        val charactersService =
            CharactersService(charactersRepository, raiderIoClient, riotClient, blizzardClient)
        val dataCacheService =
            DataCacheService(dataCacheRepository, charactersRepository, raiderIoClient, riotClient, blizzardClient, retryConfig)
        return Triple(charactersService, spyk(dataCacheService), dataCacheRepository)
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
        processor: suspend (EventWithVersion, CharactersService, DataCacheService) -> Either<ControllerError, Unit>,
        gameToVerify: Game,
        characterToVerify: Character,
        eventWithVersion: EventWithVersion,
        charactersService: CharactersService,
        dataCacheService: DataCacheService,
        dataCacheRepository: DataCacheRepository,
        shouldCache: Boolean,
        expectedCacheSize: Int
    ) {
        val result =
            processor(eventWithVersion, charactersService, dataCacheService)
        result.fold(
            { fail("Expected success") },
            {
                if (shouldCache) coVerify {
                    dataCacheService.cache(
                        eq(listOf(characterToVerify)), eq(
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
            Mockito.`when`(riotClient.getLeagueEntriesBySummonerId(CharactersTestHelper.basicLolCharacter.summonerId))
                .thenReturn(Either.Right(listOf()))

            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewCreatedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    ViewsTestHelper.owner,
                    listOf(CharactersTestHelper.basicLolCharacter.id),
                    true,
                    Game.LOL,
                    ViewsTestHelper.featured
                ), Game.LOL
            )

            assertCacheInvocation(
                EventSubscription::syncLolCharactersProcessor,
                Game.LOL,
                CharactersTestHelper.basicLolCharacter,
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
                        listOf(CharactersTestHelper.basicLolCharacter.id),
                        true,
                        Game.WOW,
                        ViewsTestHelper.featured
                    ), Game.WOW
                )

                assertCacheInvocation(
                    EventSubscription::syncLolCharactersProcessor,
                    Game.LOL,
                    CharactersTestHelper.basicLolCharacter,
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
            Mockito.`when`(riotClient.getLeagueEntriesBySummonerId(CharactersTestHelper.basicLolCharacter.summonerId))
                .thenReturn(Either.Right(listOf()))
            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewEditedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    listOf(CharactersTestHelper.basicLolCharacter.id),
                    true,
                    Game.LOL,
                    ViewsTestHelper.featured
                ), Game.LOL
            )

            assertCacheInvocation(
                EventSubscription::syncLolCharactersProcessor,
                Game.LOL,
                CharactersTestHelper.basicLolCharacter,
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
                        listOf(CharactersTestHelper.basicLolCharacter.id),
                        true,
                        Game.WOW,
                        ViewsTestHelper.featured
                    ), Game.WOW
                )

                assertCacheInvocation(
                    EventSubscription::syncLolCharactersProcessor,
                    Game.LOL,
                    CharactersTestHelper.basicLolCharacter,
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
            Mockito.`when`(riotClient.getLeagueEntriesBySummonerId(CharactersTestHelper.basicLolCharacter.summonerId))
                .thenReturn(Either.Right(listOf()))
            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewPatchedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    listOf(CharactersTestHelper.basicLolCharacter.id),
                    true,
                    Game.LOL,
                    ViewsTestHelper.featured
                ), Game.LOL
            )

            assertCacheInvocation(
                EventSubscription::syncLolCharactersProcessor,
                Game.LOL,
                CharactersTestHelper.basicLolCharacter,
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
                        listOf(CharactersTestHelper.basicLolCharacter.id),
                        true,
                        Game.WOW,
                        ViewsTestHelper.featured
                    ), Game.WOW
                )

                assertCacheInvocation(
                    EventSubscription::syncLolCharactersProcessor,
                    Game.LOL,
                    CharactersTestHelper.basicLolCharacter,
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
                        listOf(CharactersTestHelper.basicWowRequest),
                        Game.LOL,
                        owner,
                        ViewsTestHelper.featured
                    ), Game.LOL
                )

                assertCacheInvocation(
                    EventSubscription::syncLolCharactersProcessor,
                    Game.LOL,
                    CharactersTestHelper.basicWowCharacter,
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
            Mockito.`when`(raiderIoClient.get(CharactersTestHelper.basicWowCharacter))
                .thenReturn(RaiderIoMockHelper.get(CharactersTestHelper.basicWowCharacter))

            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewCreatedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    ViewsTestHelper.owner,
                    listOf(CharactersTestHelper.basicWowCharacter.id),
                    true,
                    Game.WOW,
                    ViewsTestHelper.featured
                ), Game.WOW
            )

            assertCacheInvocation(
                EventSubscription::syncWowCharactersProcessor,
                Game.WOW,
                CharactersTestHelper.basicWowCharacter,
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
            Mockito.`when`(raiderIoClient.get(CharactersTestHelper.basicWowCharacter))
                .thenReturn(RaiderIoMockHelper.get(CharactersTestHelper.basicWowCharacter))
            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewEditedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    listOf(CharactersTestHelper.basicWowCharacter.id),
                    true,
                    Game.WOW,
                    ViewsTestHelper.featured
                ), Game.WOW
            )

            assertCacheInvocation(
                EventSubscription::syncWowCharactersProcessor,
                Game.WOW,
                CharactersTestHelper.basicWowCharacter,
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
            Mockito.`when`(raiderIoClient.get(CharactersTestHelper.basicWowCharacter))
                .thenReturn(RaiderIoMockHelper.get(CharactersTestHelper.basicWowCharacter))
            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewPatchedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    listOf(CharactersTestHelper.basicWowCharacter.id),
                    true,
                    Game.WOW,
                    ViewsTestHelper.featured
                ), Game.WOW
            )

            assertCacheInvocation(
                EventSubscription::syncWowCharactersProcessor,
                Game.WOW,
                CharactersTestHelper.basicWowCharacter,
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
                        listOf(CharactersTestHelper.basicWowRequest),
                        Game.WOW,
                        owner,
                        ViewsTestHelper.featured
                    ), Game.WOW
                )

                assertCacheInvocation(
                    EventSubscription::syncWowCharactersProcessor,
                    Game.WOW,
                    CharactersTestHelper.basicWowCharacter,
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
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(
                BlizzardMockHelper.getCharacterProfile(
                    CharactersTestHelper.basicWowCharacter
                )
            )
            Mockito.`when`(
                blizzardClient.getCharacterMedia(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(
                BlizzardMockHelper.getCharacterMedia(
                    CharactersTestHelper.basicWowCharacter
                )
            )
            Mockito.`when`(
                blizzardClient.getCharacterEquipment(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterEquipment())

            Mockito.`when`(
                blizzardClient.getCharacterStats(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterStats())

            Mockito.`when`(
                blizzardClient.getCharacterSpecializations(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterSpecializations())

            Mockito.`when`(
                blizzardClient.getItemMedia(
                    CharactersTestHelper.basicWowCharacter.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getItemMedia())

            Mockito.`when`(
                blizzardClient.getItem(
                    CharactersTestHelper.basicWowCharacter.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getWowItemResponse())

            Mockito.`when`(
                raiderIoClient.wowheadEmbeddedCalculator(CharactersTestHelper.basicWowCharacter)
            ).thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))

            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewCreatedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    ViewsTestHelper.owner,
                    listOf(CharactersTestHelper.basicWowCharacter.id),
                    true,
                    Game.WOW_HC,
                    ViewsTestHelper.featured
                ), Game.WOW_HC
            )

            assertCacheInvocation(
                EventSubscription::syncWowHardcoreCharactersProcessor,
                Game.WOW_HC,
                CharactersTestHelper.basicWowCharacter,
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
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(
                BlizzardMockHelper.getCharacterProfile(
                    CharactersTestHelper.basicWowCharacter
                )
            )
            Mockito.`when`(
                blizzardClient.getCharacterMedia(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(
                BlizzardMockHelper.getCharacterMedia(
                    CharactersTestHelper.basicWowCharacter
                )
            )
            Mockito.`when`(
                blizzardClient.getCharacterEquipment(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterEquipment())

            Mockito.`when`(
                blizzardClient.getCharacterStats(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterStats())

            Mockito.`when`(
                blizzardClient.getCharacterSpecializations(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterSpecializations())

            Mockito.`when`(
                blizzardClient.getItemMedia(
                    CharactersTestHelper.basicWowCharacter.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getItemMedia())

            Mockito.`when`(
                blizzardClient.getItem(
                    CharactersTestHelper.basicWowCharacter.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getWowItemResponse())

            Mockito.`when`(
                raiderIoClient.wowheadEmbeddedCalculator(CharactersTestHelper.basicWowCharacter)
            ).thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))

            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewEditedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    listOf(CharactersTestHelper.basicWowCharacter.id),
                    true,
                    Game.WOW_HC,
                    ViewsTestHelper.featured
                ), Game.WOW_HC
            )

            assertCacheInvocation(
                EventSubscription::syncWowHardcoreCharactersProcessor,
                Game.WOW_HC,
                CharactersTestHelper.basicWowCharacter,
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
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(
                BlizzardMockHelper.getCharacterProfile(
                    CharactersTestHelper.basicWowCharacter
                )
            )
            Mockito.`when`(
                blizzardClient.getCharacterMedia(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(
                BlizzardMockHelper.getCharacterMedia(
                    CharactersTestHelper.basicWowCharacter
                )
            )
            Mockito.`when`(
                blizzardClient.getCharacterEquipment(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterEquipment())

            Mockito.`when`(
                blizzardClient.getCharacterStats(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterStats())

            Mockito.`when`(
                blizzardClient.getCharacterSpecializations(
                    CharactersTestHelper.basicWowCharacter.region,
                    CharactersTestHelper.basicWowCharacter.realm,
                    CharactersTestHelper.basicWowCharacter.name
                )
            ).thenReturn(BlizzardMockHelper.getCharacterSpecializations())

            Mockito.`when`(
                blizzardClient.getItemMedia(
                    CharactersTestHelper.basicWowCharacter.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getItemMedia())

            Mockito.`when`(
                blizzardClient.getItem(
                    CharactersTestHelper.basicWowCharacter.region,
                    18421
                )
            ).thenReturn(BlizzardMockHelper.getWowItemResponse())

            Mockito.`when`(
                raiderIoClient.wowheadEmbeddedCalculator(CharactersTestHelper.basicWowCharacter)
            ).thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))

            val (charactersService, spiedService, dataCacheRepository) = createService()
            val eventWithVersion = createEventWithVersion(
                ViewPatchedEvent(
                    ViewsTestHelper.id,
                    ViewsTestHelper.name,
                    listOf(CharactersTestHelper.basicWowCharacter.id),
                    true,
                    Game.WOW_HC,
                    ViewsTestHelper.featured
                ), Game.WOW_HC
            )

            assertCacheInvocation(
                EventSubscription::syncWowHardcoreCharactersProcessor,
                Game.WOW_HC,
                CharactersTestHelper.basicWowCharacter,
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
                        listOf(CharactersTestHelper.basicWowRequest),
                        Game.WOW_HC,
                        owner,
                        ViewsTestHelper.featured
                    ), Game.WOW_HC
                )

                assertCacheInvocation(
                    EventSubscription::syncWowHardcoreCharactersProcessor,
                    Game.WOW_HC,
                    CharactersTestHelper.basicWowCharacter,
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