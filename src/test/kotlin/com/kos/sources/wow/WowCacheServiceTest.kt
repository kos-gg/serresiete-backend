package com.kos.sources.wow

import arrow.core.Either
import com.kos.clients.HttpError
import com.kos.clients.domain.EnrichedMythicPlusRun
import com.kos.clients.domain.MythicPlusRank
import com.kos.clients.domain.MythicPlusRanks
import com.kos.clients.domain.MythicPlusRun
import com.kos.clients.domain.MythicPlusSeasonScore
import com.kos.clients.domain.RaiderIoData
import com.kos.clients.domain.RaiderIoProfile
import com.kos.clients.domain.RaiderIoResponse
import com.kos.clients.domain.SeasonScores
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.raiderio.RaiderIoHttpClientHelper
import com.kos.datacache.RaiderIoMockHelper
import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.EntitiesTestHelper
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.sources.wow.staticdata.wowseason.WowSeason
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonsState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import kotlin.test.Test

class WowCacheServiceTest {

    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val decodeJson = Json { ignoreUnknownKeys = true }
    private val season = WowSeason(1, "Default Season", "default-season", 1, "", true)

    private suspend fun seasonRepository() =
        WowSeasonInMemoryRepository().withState(WowSeasonsState(listOf(season)))

    private fun responseWithRun(run: MythicPlusRun) = Either.Right(
        RaiderIoResponse(
            RaiderIoProfile(
                basicWowEntity.name, basicWowEntity.realm, basicWowEntity.region, "class", "spec",
                listOf(MythicPlusSeasonScore("df-3", SeasonScores(0.0, 0.0, 0.0, 0.0, 0.0))),
                MythicPlusRanks(MythicPlusRank(1, 1, 1), MythicPlusRank(1, 1, 1), mapOf()),
                listOf(run)
            ),
            listOf()
        )
    )

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

            WowEntitySynchronizer(repo, raiderIoClient, seasonRepository())
                .synchronize(listOf(basicWowEntity, basicWowEntity.copy(id = 2)))
            assertEquals(3, repo.state().size)
        }
    }

    @Test
    fun `run details are embedded in cached data`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun
            val runDetails = RaiderIoHttpClientHelper.runDetails

            `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity)).thenReturn(responseWithRun(run))
            `when`(raiderIoClient.getRunDetails(season.slug, run.runId.toString()))
                .thenReturn(Either.Right(runDetails))

            val repo = DataCacheInMemoryRepository()
            WowEntitySynchronizer(repo, raiderIoClient, seasonRepository())
                .synchronize(listOf(basicWowEntity))

            val cached: RaiderIoData =
                decodeJson.decodeFromString(repo.get(basicWowEntity.id).first().data)
            assertEquals(EnrichedMythicPlusRun(run, runDetails), cached.mythicPlusBestRuns.first())
        }
    }

    @Test
    fun `data is still cached with null run details when getRunDetails fails`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun

            `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity)).thenReturn(responseWithRun(run))
            `when`(raiderIoClient.getRunDetails(season.slug, run.runId.toString()))
                .thenReturn(Either.Left(HttpError(500, "Internal server error")))

            val repo = DataCacheInMemoryRepository()
            val errors = WowEntitySynchronizer(repo, raiderIoClient, seasonRepository())
                .synchronize(listOf(basicWowEntity))

            assertTrue(errors.isNotEmpty())
            val cached: RaiderIoData =
                decodeJson.decodeFromString(repo.get(basicWowEntity.id).first().data)
            assertNull(cached.mythicPlusBestRuns.first().details)
        }
    }

    @Test
    fun `getRunDetails is called only once for the same run id across multiple entities`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun
            val runDetails = RaiderIoHttpClientHelper.runDetails

            `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity)).thenReturn(responseWithRun(run))
            `when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity2)).thenReturn(responseWithRun(run))
            `when`(raiderIoClient.getRunDetails(season.slug, run.runId.toString()))
                .thenReturn(Either.Right(runDetails))

            val repo = DataCacheInMemoryRepository()
            WowEntitySynchronizer(repo, raiderIoClient, seasonRepository())
                .synchronize(listOf(basicWowEntity, EntitiesTestHelper.basicWowEntity2))

            verify(raiderIoClient, times(1)).getRunDetails(season.slug, run.runId.toString())
        }
    }
}