package com.kos.datacache

import arrow.core.Either
import com.kos.characters.CharactersTestHelper.basicLolCharacter
import com.kos.characters.CharactersTestHelper.basicWowCharacter
import com.kos.characters.CharactersTestHelper.basicWowHardcoreCharacter
import com.kos.characters.repository.CharactersInMemoryRepository
import com.kos.characters.repository.CharactersState
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.domain.Metadata
import com.kos.clients.domain.QueueType
import com.kos.clients.domain.RaiderIoData
import com.kos.clients.domain.RiotData
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.riot.RiotClient
import com.kos.common.JsonParseError
import com.kos.common.RetryConfig
import com.kos.datacache.RiotMockHelper.flexQEntryResponse
import com.kos.datacache.TestHelper.lolDataCache
import com.kos.datacache.TestHelper.smartSyncDataCache
import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.views.Game
import com.kos.views.ViewsTestHelper.basicSimpleWowHardcoreView
import com.kos.views.repository.ViewsInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertFalse
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class DataCacheServiceTest {
    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val riotClient = mock(RiotClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val retryConfig = RetryConfig(1, 1)

    @Test
    fun `i can get wow data`() {
        runBlocking {
            val repo = DataCacheInMemoryRepository().withState(
                listOf(
                    wowDataCache,
                    wowDataCache.copy(characterId = 2, data = wowDataCache.data.replace(""""id": 1""", """"id": 2""")),
                    wowDataCache.copy(characterId = 3, data = wowDataCache.data.replace(""""id": 1""", """"id": 3"""))
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
            assertEquals(listOf(basicLolCharacter.name), data.map {
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
            `when`(raiderIoClient.get(basicWowCharacter)).thenReturn(RaiderIoMockHelper.get(basicWowCharacter))
            `when`(raiderIoClient.get(basicWowCharacter.copy(id = 2))).thenReturn(
                RaiderIoMockHelper.get(basicWowCharacter.copy(id = 2))
            )
            `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
            val repo = DataCacheInMemoryRepository().withState(listOf(wowDataCache))
            assertEquals(listOf(wowDataCache), repo.state())
            createService(repo).cache(listOf(basicWowCharacter, basicWowCharacter.copy(id = 2)), Game.WOW)
            assertEquals(3, repo.state().size)
        }
    }

    @Test
    fun `i can cache lol data`() {
        runBlocking {
            `when`(riotClient.getLeagueEntriesBySummonerId(basicLolCharacter.summonerId)).thenReturn(RiotMockHelper.leagueEntries)
            `when`(riotClient.getMatchesByPuuid(basicLolCharacter.puuid, QueueType.FLEX_Q.toInt())).thenReturn(
                RiotMockHelper.matches
            )
            `when`(riotClient.getMatchesByPuuid(basicLolCharacter.puuid, QueueType.SOLO_Q.toInt())).thenReturn(
                RiotMockHelper.matches
            )
            `when`(riotClient.getMatchById(RiotMockHelper.matchId)).thenReturn(Either.Right(RiotMockHelper.match))
            val repo = DataCacheInMemoryRepository()
            createService(repo).cache(listOf(basicLolCharacter), Game.LOL)
            assertEquals(1, repo.state().size)
        }
    }

    @Test
    fun `caching lol data behaves smart and does not attempt to retrieve matches that are present on newest cached record`() {
        runBlocking {

            val newMatchIds = listOf("match1", "match2", "match3", "match4", "match5")
            val dataCache = DataCache(1, smartSyncDataCache, OffsetDateTime.now().minusHours(5), Game.LOL)

            `when`(riotClient.getLeagueEntriesBySummonerId(basicLolCharacter.summonerId))
                .thenReturn(Either.Right(listOf(flexQEntryResponse)))
            `when`(
                riotClient.getMatchesByPuuid(basicLolCharacter.puuid, QueueType.FLEX_Q.toInt())
            ).thenReturn(Either.Right(newMatchIds))

            `when`(riotClient.getMatchById(anyString())).thenReturn(Either.Right(RiotMockHelper.match))

            val repo = DataCacheInMemoryRepository().withState(listOf(dataCache))
            val errors = createService(repo).cache(listOf(basicLolCharacter), Game.LOL)

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
            `when`(riotClient.getLeagueEntriesBySummonerId(basicLolCharacter.summonerId))
                .thenReturn(jsonParseError)

            val repo = DataCacheInMemoryRepository()
            val errors = createService(repo).cache(listOf(basicLolCharacter), Game.LOL)

            assertEquals(listOf(jsonParseError.value), errors)
        }
    }

    @Test
    fun `caching lol data behaves smart, retrieves only necessary matches, and inserts only the requested matches`() {
        runBlocking {
            val requestedMatchIds = listOf("match3", "match4", "match5", "match6", "match7")
            val dataCache = DataCache(1, smartSyncDataCache, OffsetDateTime.now().minusHours(5), Game.LOL)

            `when`(riotClient.getLeagueEntriesBySummonerId(basicLolCharacter.summonerId))
                .thenReturn(Either.Right(listOf(flexQEntryResponse)))
            `when`(
                riotClient.getMatchesByPuuid(basicLolCharacter.puuid, QueueType.FLEX_Q.toInt())
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
            val errors = createService(repo).cache(listOf(basicLolCharacter), Game.LOL)

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

            `when`(riotClient.getLeagueEntriesBySummonerId(basicLolCharacter.summonerId))
                .thenReturn(Either.Right(listOf(flexQEntryResponse)))
            `when`(
                riotClient.getMatchesByPuuid(basicLolCharacter.puuid, QueueType.FLEX_Q.toInt())
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
            val errors = createService(repo).cache(listOf(basicLolCharacter, basicLolCharacter.copy(id = 2)), Game.LOL)

            verify(riotClient, times(1)).getMatchById("match3")
            verify(riotClient, times(1)).getMatchById("match4")
            verify(riotClient, times(1)).getMatchById("match5")
            verify(riotClient, times(1)).getMatchById("match6")
            verify(riotClient, times(1)).getMatchById("match7")

            assertEquals(listOf(), errors)
        }
    }

    private suspend fun createService(dataCacheRepository: DataCacheInMemoryRepository): DataCacheService {
        val viewsRepository = ViewsInMemoryRepository()
            .withState(listOf(basicSimpleWowHardcoreView.copy(characterIds = listOf(1))))
        val charactersRepository = CharactersInMemoryRepository(dataCacheRepository, viewsRepository)
            .withState(
                CharactersState(
                    listOf(),
                    listOf(basicWowHardcoreCharacter, basicWowHardcoreCharacter.copy(id = 2, blizzardId = 123)),
                    listOf()
                )
            )

        return DataCacheService(
            dataCacheRepository,
            charactersRepository,
            raiderIoClient,
            riotClient,
            blizzardClient,
            retryConfig
        )
    }

}