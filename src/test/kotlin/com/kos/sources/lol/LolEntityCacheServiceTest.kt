package com.kos.sources.lol

import arrow.core.Either
import com.kos.clients.domain.QueueType
import com.kos.clients.riot.RiotClient
import com.kos.common.error.SyncProcessingError
import com.kos.datacache.DataCache
import com.kos.datacache.RiotMockHelper
import com.kos.datacache.RiotMockHelper.flexQEntryResponse
import com.kos.datacache.TestHelper.smartSyncDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper.basicLolEntity
import com.kos.views.Game
import kotlinx.coroutines.runBlocking
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mockito.*
import java.time.OffsetDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue


class LolEntityCacheServiceTest {
    private val riotClient = mock(RiotClient::class.java)

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
            LolEntitySynchronizer(repo, riotClient)
                .synchronize(listOf(basicLolEntity))
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
            val errors = LolEntitySynchronizer(repo, riotClient)
                .synchronize(listOf(basicLolEntity))

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

            val jsonParseError = Either.Left(com.kos.clients.JsonParseError("{}", ""))
            `when`(riotClient.getLeagueEntriesByPUUID(basicLolEntity.puuid))
                .thenReturn(jsonParseError)

            val repo = DataCacheInMemoryRepository()
            val errors = LolEntitySynchronizer(repo, riotClient)
                .synchronize(listOf(basicLolEntity))

            errors.forEach { error ->
                error as SyncProcessingError
                assertTrue((error.type == "JSON_PARSE_ERROR"))
            }
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
                        metadata = com.kos.clients.domain.Metadata(
                            "match4"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match5")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = com.kos.clients.domain.Metadata(
                            "match5"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match6")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = com.kos.clients.domain.Metadata(
                            "match6"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match7")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = com.kos.clients.domain.Metadata(
                            "match7"
                        )
                    )
                )
            )

            val repo = DataCacheInMemoryRepository().withState(listOf(dataCache))
            val errors = LolEntitySynchronizer(repo, riotClient)
                .synchronize(listOf(basicLolEntity))

            verify(riotClient, times(0)).getMatchById("match3")

            verify(riotClient, times(1)).getMatchById("match4")
            verify(riotClient, times(1)).getMatchById("match5")
            verify(riotClient, times(1)).getMatchById("match6")
            verify(riotClient, times(1)).getMatchById("match7")

            val insertedValue = repo.get(1).maxBy { it.inserted }
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
                        metadata = com.kos.clients.domain.Metadata(
                            "match4"
                        )
                    )
                )
            )

            `when`(riotClient.getMatchById("match4")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = com.kos.clients.domain.Metadata(
                            "match4"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match5")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = com.kos.clients.domain.Metadata(
                            "match5"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match6")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = com.kos.clients.domain.Metadata(
                            "match6"
                        )
                    )
                )
            )
            `when`(riotClient.getMatchById("match7")).thenReturn(
                Either.Right(
                    RiotMockHelper.match.copy(
                        metadata = com.kos.clients.domain.Metadata(
                            "match7"
                        )
                    )
                )
            )

            val repo = DataCacheInMemoryRepository()
            val errors = LolEntitySynchronizer(repo, riotClient)
                .synchronize(listOf(basicLolEntity, basicLolEntity.copy(id = 2)))

            verify(riotClient, times(1)).getMatchById("match3")
            verify(riotClient, times(1)).getMatchById("match4")
            verify(riotClient, times(1)).getMatchById("match5")
            verify(riotClient, times(1)).getMatchById("match6")
            verify(riotClient, times(1)).getMatchById("match7")

            assertEquals(listOf(), errors)
        }
    }
}