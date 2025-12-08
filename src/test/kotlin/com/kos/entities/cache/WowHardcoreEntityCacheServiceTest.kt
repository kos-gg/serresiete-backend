package com.kos.entities.cache

import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.BlizzardDatabaseClient
import com.kos.clients.domain.HardcoreData
import com.kos.clients.domain.RaiderioWowHeadEmbeddedResponse
import com.kos.clients.domain.TalentLoadout
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.NotFoundHardcoreCharacter
import com.kos.common.RetryConfig
import com.kos.common.UnableToSyncEntityError
import com.kos.datacache.BlizzardMockHelper.getCharacterEquipment
import com.kos.datacache.BlizzardMockHelper.getCharacterMedia
import com.kos.datacache.BlizzardMockHelper.getCharacterSpecializations
import com.kos.datacache.BlizzardMockHelper.getCharacterStats
import com.kos.datacache.BlizzardMockHelper.getItemMedia
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponse
import com.kos.datacache.BlizzardMockHelper.getWowItemResponse
import com.kos.datacache.TestHelper.wowHardcoreDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.views.Game
import com.kos.views.ViewEntity
import com.kos.views.ViewsTestHelper.basicSimpleWowHardcoreView
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test

class WowHardcoreEntityCacheServiceTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val blizzardDatabaseClient = mock(BlizzardDatabaseClient::class.java)
    private val retryConfig = RetryConfig(1, 1)

    private val json = Json {
        ignoreUnknownKeys = true
    }

    @Test
    fun `the wow hardcore cache service retrieves a dead character and this character is not processed `() {
        runBlocking {
            val dataCacheRepository = DataCacheInMemoryRepository().withState(
                listOf(
                    wowHardcoreDataCache.copy(
                        data = wowHardcoreDataCache.data.replace(
                            """"isDead": false""",
                            """"isDead": true"""
                        )
                    )
                )
            )

            val wowHardcoreEntityCacheService = WowHardcoreEntityCacheService(
                dataCacheRepository,
                EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                blizzardDatabaseClient,
                retryConfig
            )

            wowHardcoreEntityCacheService.cache(
                listOf(
                    basicWowHardcoreEntity
                )
            )

            dataCacheRepository.get(basicWowEntity.id).maxByOrNull { it.inserted }?.let {
                val expectedHardcoreData = json.decodeFromString<HardcoreData>(it.data)
                assertTrue(expectedHardcoreData.isDead)
            }
            assertEquals(1, dataCacheRepository.state().size)
        }
    }


    @Test
    fun `the wow hardcore cache service deletes the wow hardcore entity if not found neither in api or data cache repository`() {
        runBlocking {
            val expectedNotFoundHardcoreCharacter = NotFoundHardcoreCharacter(basicWowHardcoreEntity.name)

            `when`(
                blizzardClient.getCharacterProfile(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            ).thenReturn(Either.Left(expectedNotFoundHardcoreCharacter))

            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf())
            val viewsRepository = ViewsInMemoryRepository()
                .withState(
                    ViewsState(
                        listOf(basicSimpleWowHardcoreView.copy(entitiesIds = listOf(1))),
                        basicSimpleWowHardcoreView.entitiesIds.map {
                            ViewEntity(
                                it,
                                basicSimpleWowHardcoreView.id,
                                "alias"
                            )
                        })
                )
            val entitiesRepository = EntitiesInMemoryRepository(dataCacheRepository, viewsRepository)
                .withState(
                    EntitiesState(
                        listOf(),
                        listOf(basicWowHardcoreEntity, basicWowHardcoreEntity.copy(id = 2, blizzardId = 123)),
                        listOf()
                    )
                )

            val cacheResult = WowHardcoreEntityCacheService(
                dataCacheRepository,
                entitiesRepository,
                raiderIoClient,
                blizzardClient,
                blizzardDatabaseClient,
                retryConfig
            ).cache(
                listOf(
                    basicWowHardcoreEntity
                )
            )

            cacheResult.any { it is UnableToSyncEntityError }
            assertNull(entitiesRepository.get(1, Game.WOW_HC))
        }
    }

    @Test
    fun `the wow hardcore cache service marks a character as dead if not found in blizzard api`() {
        runBlocking {
            val expectedNotFoundHardcoreCharacter = NotFoundHardcoreCharacter(basicWowHardcoreEntity.name)

            `when`(
                blizzardClient.getCharacterProfile(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            ).thenReturn(Either.Left(expectedNotFoundHardcoreCharacter))

            val dataCacheRepository = DataCacheInMemoryRepository().withState(listOf(wowHardcoreDataCache))
            WowHardcoreEntityCacheService(
                dataCacheRepository,
                EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                blizzardDatabaseClient,
                retryConfig
            ).cache(
                listOf(
                    basicWowHardcoreEntity
                )
            )
            dataCacheRepository.get(basicWowHardcoreEntity.id).maxByOrNull { it.inserted }?.let {
                val expectedHardcoreData = json.decodeFromString<HardcoreData>(it.data)
                assertTrue(expectedHardcoreData.isDead)
            }
            assertEquals(2, dataCacheRepository.state().size)
        }
    }

    @Test
    fun `the wow hardcore cache service marks a character as dead when it is found but with different blizzard id`() {
        runBlocking {

            `when`(
                blizzardClient.getCharacterProfile(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            ).thenReturn(Either.Right(getWowCharacterResponse))

            val dataCacheRepository = DataCacheInMemoryRepository().withState(
                listOf(
                    wowHardcoreDataCache
                )
            )

            WowHardcoreEntityCacheService(
                dataCacheRepository,
                EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                blizzardDatabaseClient,
                retryConfig
            ).cache(
                listOf(
                    basicWowHardcoreEntity
                )
            )

            dataCacheRepository.get(basicWowHardcoreEntity.id).maxByOrNull { it.inserted }?.let {
                val expectedHardcoreData = json.decodeFromString<HardcoreData>(it.data)
                assertTrue(expectedHardcoreData.isDead)
            }
            assertEquals(2, dataCacheRepository.state().size)
        }
    }

    @Test
    fun `the wow hardcore cache service inserts a new cache entry when there is no recent data and character is found in blizzard api`() {
        runBlocking {
            `when`(
                blizzardClient.getCharacterProfile(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            ).thenReturn(Either.Right(getWowCharacterResponse.copy(id = 12345)))
            `when`(
                blizzardClient.getCharacterMedia(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            ).thenReturn(getCharacterMedia(basicWowHardcoreEntity))
            `when`(
                blizzardClient.getCharacterEquipment(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            ).thenReturn(getCharacterEquipment())
            `when`(
                blizzardClient.getCharacterStats(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            ).thenReturn(getCharacterStats())
            `when`(
                blizzardClient.getCharacterSpecializations(
                    basicWowHardcoreEntity.region,
                    basicWowHardcoreEntity.realm,
                    basicWowHardcoreEntity.name
                )
            ).thenReturn(getCharacterSpecializations())
            `when`(
                blizzardClient.getItemMedia(
                    basicWowHardcoreEntity.region,
                    18421
                )
            ).thenReturn(getItemMedia())
            `when`(
                blizzardClient.getItem(
                    basicWowHardcoreEntity.region,
                    18421
                )
            ).thenReturn(getWowItemResponse())
            `when`(
                raiderIoClient.wowheadEmbeddedCalculator(basicWowHardcoreEntity)
            ).thenReturn(Either.Right(RaiderioWowHeadEmbeddedResponse(TalentLoadout("030030303-02020202-"))))

            val dataCacheRepository = DataCacheInMemoryRepository().withState(
                listOf()
            )

            WowHardcoreEntityCacheService(
                dataCacheRepository,
                EntitiesInMemoryRepository(),
                raiderIoClient,
                blizzardClient,
                blizzardDatabaseClient,
                retryConfig
            ).cache(
                listOf(
                    basicWowHardcoreEntity
                )
            )

            dataCacheRepository.get(basicWowHardcoreEntity.id).maxByOrNull { it.inserted }?.let {
                val expectedHardcoreData = json.decodeFromString<HardcoreData>(it.data)
                assertFalse(expectedHardcoreData.isDead)
            }
            assertEquals(1, dataCacheRepository.state().size)
        }
    }
}