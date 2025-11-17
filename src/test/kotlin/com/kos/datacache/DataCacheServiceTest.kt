package com.kos.datacache

import arrow.core.Either
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.blizzard.BlizzardDatabaseClient
import com.kos.clients.domain.*
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.JsonParseError
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
import com.kos.datacache.RiotMockHelper.flexQEntryResponse
import com.kos.datacache.TestHelper.lolDataCache
import com.kos.datacache.TestHelper.smartSyncDataCache
import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.TestHelper.wowHardcoreDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.EntitiesTestHelper.basicWowHardcoreEntity
import com.kos.entities.repository.EntitiesInMemoryRepository
import com.kos.entities.repository.EntitiesState
import com.kos.eventsourcing.events.repository.EventStoreInMemory
import com.kos.views.Game
import com.kos.views.ViewEntity
import com.kos.views.ViewsTestHelper.basicSimpleWowHardcoreView
import com.kos.views.repository.ViewsInMemoryRepository
import com.kos.views.repository.ViewsState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataCacheServiceTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
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

            val dataCacheService = createService(dataCacheRepository)
            dataCacheService.cache(
                listOf(
                    basicWowHardcoreEntity
                ), Game.WOW_HC
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
            val eventsStore = EventStoreInMemory()

            val dataCacheService = DataCacheService(
                dataCacheRepository,
                entitiesRepository,
                raiderIoClient,
                riotClient,
                blizzardClient,
                blizzardDatabaseClient,
                retryConfig,
                eventsStore
            )

            val cacheResult = dataCacheService.cache(
                listOf(
                    basicWowHardcoreEntity
                ), Game.WOW_HC
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
            val dataCacheService = createService(dataCacheRepository)
            dataCacheService.cache(
                listOf(
                    basicWowHardcoreEntity
                ), Game.WOW_HC
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

            val dataCacheService = createService(dataCacheRepository)

            dataCacheService.cache(
                listOf(
                    basicWowHardcoreEntity
                ), Game.WOW_HC
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

            val dataCacheService = createService(dataCacheRepository)

            dataCacheService.cache(
                listOf(
                    basicWowHardcoreEntity
                ), Game.WOW_HC
            )

            dataCacheRepository.get(basicWowHardcoreEntity.id).maxByOrNull { it.inserted }?.let {
                val expectedHardcoreData = json.decodeFromString<HardcoreData>(it.data)
                assertFalse(expectedHardcoreData.isDead)
            }
            assertEquals(1, dataCacheRepository.state().size)
        }
    }

    @Test
    fun `i can get wow data`() {
        runBlocking {
            val repo = DataCacheInMemoryRepository().withState(
                listOf(
                    wowDataCache,
                    wowDataCache.copy(entityId = 2, data = wowDataCache.data.replace(""""id": 1""", """"id": 2""")),
                    wowDataCache.copy(entityId = 3, data = wowDataCache.data.replace(""""id": 1""", """"id": 3"""))
                )
            )

            val data = createService(repo).getData(listOf(1, 3), oldFirst = true)
            assertTrue(data.isRight { it.size == 2 })
            assertEquals(listOf<Long>(1, 3), data.map {
                it.map { d ->
                    d as RaiderIoData
                    d.id
                }
            }.getOrNull())
        }
    }

    @Test
    fun `i can get lol data`() {
        runBlocking {
            val repo = DataCacheInMemoryRepository().withState(
                listOf(lolDataCache)
            )
            val data = createService(repo).getData(listOf(2), oldFirst = true)

            assertTrue(data.isRight { it.size == 1 })
            assertEquals(listOf(basicLolEntity.name), data.map {
                it.map { d ->
                    d as RiotData
                    d.summonerName
                }
            }.getOrNull())
        }
    }

    @Test
    fun `i can verify that getting data returns the oldest cached data record stored`() {
        runBlocking {
            val repo = DataCacheInMemoryRepository().withState(
                listOf(
                    wowDataCache.copy(inserted = wowDataCache.inserted.minusHours(10)),
                    wowDataCache.copy(data = wowDataCache.data.replace(""""score": 0.0""", """"score": 1.0""")),
                )
            )
            val data = createService(repo).getData(listOf(1), oldFirst = true)
            assertTrue(data.isRight { it.size == 1 })
            assertEquals(listOf(0.0), data.map {
                it.map { d ->
                    d as RaiderIoData
                    d.score
                }
            }.getOrNull())
        }
    }

    @Test
    fun `i can cache wow data`() {
        runBlocking {
            `when`(raiderIoClient.get(basicWowEntity)).thenReturn(RaiderIoMockHelper.get(basicWowEntity))
            `when`(raiderIoClient.get(basicWowEntity.copy(id = 2))).thenReturn(
                RaiderIoMockHelper.get(basicWowEntity.copy(id = 2))
            )
            `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
            val repo = DataCacheInMemoryRepository().withState(listOf(wowDataCache))
            assertEquals(listOf(wowDataCache), repo.state())
            createService(repo).cache(listOf(basicWowEntity, basicWowEntity.copy(id = 2)), Game.WOW)
            assertEquals(3, repo.state().size)
        }
    }

    @Test
    fun `i can cache lol data`() {
        runBlocking {
            `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid)).thenReturn(RiotMockHelper.leagueEntries)
            `when`(riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.FLEX_Q.toInt())).thenReturn(
                RiotMockHelper.matches
            )
            `when`(riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.SOLO_Q.toInt())).thenReturn(
                RiotMockHelper.matches
            )
            `when`(riotClient.getMatchById(RiotMockHelper.matchId)).thenReturn(Either.Right(RiotMockHelper.match))
            val repo = DataCacheInMemoryRepository()
            createService(repo).cache(listOf(basicLolEntity), Game.LOL)
            assertEquals(1, repo.state().size)
        }
    }

    @Test
    fun `caching lol data behaves smart and does not attempt to retrieve matches that are present on newest cached record`() {
        runBlocking {
            val newMatchIds = listOf("match1", "match2", "match3", "match4", "match5")
            val dataCache = DataCache(1, smartSyncDataCache, OffsetDateTime.now().minusHours(5), Game.LOL)

            `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid))
                .thenReturn(Either.Right(listOf(flexQEntryResponse)))
            `when`(
                riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.FLEX_Q.toInt())
            ).thenReturn(Either.Right(newMatchIds))

            `when`(riotClient.getMatchById(anyString())).thenReturn(Either.Right(RiotMockHelper.match))

            val repo = DataCacheInMemoryRepository().withState(listOf(dataCache))
            val errors = createService(repo).cache(listOf(basicLolEntity), Game.LOL)

            verify(riotClient, times(0)).getMatchById("match1")
            verify(riotClient, times(0)).getMatchById("match2")
            verify(riotClient, times(0)).getMatchById("match3")

            verify(riotClient, times(1)).getMatchById("match4")
            verify(riotClient, times(1)).getMatchById("match5")

            assertEquals(listOf(), errors)
        }
    }

    @Test
    fun `caching lol data returns an error when retrieving match data fails`() {
        runBlocking {

            val jsonParseError = Either.Left(JsonParseError("{}", ""))
            `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid))
                .thenReturn(jsonParseError)

            val repo = DataCacheInMemoryRepository()
            val errors = createService(repo).cache(listOf(basicLolEntity), Game.LOL)

            assertEquals(listOf(jsonParseError.value), errors)
        }
    }

    @Test
    fun `caching lol data behaves smart, retrieves only necessary matches, and inserts only the requested matches`() {
        runBlocking {
            val requestedMatchIds = listOf("match3", "match4", "match5", "match6", "match7")
            val dataCache = DataCache(1, smartSyncDataCache, OffsetDateTime.now().minusHours(5), Game.LOL)

            `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid))
                .thenReturn(Either.Right(listOf(flexQEntryResponse)))
            `when`(
                riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.FLEX_Q.toInt())
            ).thenReturn(Either.Right(requestedMatchIds))

            `when`(riotClient.getMatchById("match4")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = Metadata(
                            "match4"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match5")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = Metadata(
                            "match5"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match6")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = Metadata(
                            "match6"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match7")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = Metadata(
                            "match7"
                        )
                    )
                )
            )

            val repo = DataCacheInMemoryRepository().withState(listOf(dataCache))
            val errors = createService(repo).cache(listOf(basicLolEntity), Game.LOL)

            verify(riotClient, times(0)).getMatchById("match3")

            verify(riotClient, times(1)).getMatchById("match4")
            verify(riotClient, times(1)).getMatchById("match5")
            verify(riotClient, times(1)).getMatchById("match6")
            verify(riotClient, times(1)).getMatchById("match7")

            val insertedValue = createService(repo).get(1).maxBy { it.inserted }
            requestedMatchIds.forEach {
                assertTrue(insertedValue.data.contains(""""id":"$it""""), "${insertedValue.data} should contain id:$it")
            }
            assertFalse(
                insertedValue.data.contains(""""id":"match1""""),
                "${insertedValue.data} should contain id:match1"
            )
            assertFalse(
                insertedValue.data.contains(""""id":"match2""""),
                "${insertedValue.data} should contain id:match2"
            )

            assertEquals(listOf(), errors)
        }
    }

    @Test
    fun `caching lol data behaves smart, retrieves only necessary matches using dynamic cache`() {
        runBlocking {
            val requestedMatchIds = listOf("match3", "match4", "match5", "match6", "match7")

            `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid))
                .thenReturn(Either.Right(listOf(flexQEntryResponse)))
            `when`(
                riotClient.getMatchesByPuuid(basicLolEntity.puuid, QueueType.FLEX_Q.toInt())
            ).thenReturn(Either.Right(requestedMatchIds))

            `when`(riotClient.getMatchById("match3")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = Metadata(
                            "match4"
                        )
                    )
                )
            )

            `when`(riotClient.getMatchById("match4")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = Metadata(
                            "match4"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match5")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = Metadata(
                            "match5"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match6")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = Metadata(
                            "match6"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match7")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = Metadata(
                            "match7"
                        )
                    )
                )
            )

            val repo = DataCacheInMemoryRepository()
            val errors = createService(repo).cache(listOf(basicLolEntity, basicLolEntity.copy(id = 2)), Game.LOL)

            verify(riotClient, times(1)).getMatchById("match3")
            verify(riotClient, times(1)).getMatchById("match4")
            verify(riotClient, times(1)).getMatchById("match5")
            verify(riotClient, times(1)).getMatchById("match6")
            verify(riotClient, times(1)).getMatchById("match7")

            assertEquals(listOf(), errors)
        }
    }

    @Test
    fun `it must not return no data and sync operation when getOrSync entity was not in the system`() {

    }

    private suspend fun createService(dataCacheRepository: DataCacheInMemoryRepository): DataCacheService {
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
                    }
                )
            )
        val entitiesRepository = EntitiesInMemoryRepository(dataCacheRepository, viewsRepository)
            .withState(
                EntitiesState(
                    listOf(),
                    listOf(basicWowHardcoreEntity, basicWowHardcoreEntity.copy(id = 2, blizzardId = 123)),
                    listOf()
                )
            )

        val eventsStore = EventStoreInMemory()

        return DataCacheService(
            dataCacheRepository,
            entitiesRepository,
            raiderIoClient,
            riotClient,
            blizzardClient,
            blizzardDatabaseClient,
            retryConfig,
            eventsStore
        )
    }

}