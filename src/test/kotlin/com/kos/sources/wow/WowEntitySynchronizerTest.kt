package com.kos.sources.wow

import arrow.core.Either
import com.kos.clients.HttpError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.datacache.domain.ClassSpec
import com.kos.datacache.domain.EnrichedMythicPlusRun
import com.kos.datacache.domain.MythicPlusRank
import com.kos.datacache.domain.MythicPlusRun
import com.kos.datacache.domain.RunDetails
import com.kos.datacache.domain.RunMember
import com.kos.datacache.domain.WowData
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.raiderio.RaiderIoHttpClientHelper
import com.kos.datacache.RaiderIoMockHelper
import com.kos.datacache.TestHelper.wowDataCache
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.EntitiesTestHelper.basicWowEntity
import com.kos.entities.domain.WowEntity
import com.kos.sources.wow.staticdata.wowseason.WowSeason
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonRepository
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonsState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Assertions.*
import org.mockito.Mockito.*
import kotlin.test.Test

class WowEntitySynchronizerTest {

    private val raiderIoClient = mock(RaiderIoClient::class.java)
    private val blizzardClient = mock(BlizzardClient::class.java)
    private val decodeJson = Json { ignoreUnknownKeys = true }
    private val season = WowSeason(1, "Default Season", "default-season", 1, "", true)

    private suspend fun seasonRepository() =
        WowSeasonInMemoryRepository().withState(WowSeasonsState(listOf(season)))

    private val runDetails = RunDetails(
        listOf(RunMember("Sanxei", "zuljin", "Mage", ClassSpec(62, "Arcane", "dps"), "Dwarf", 328))
    )

    private suspend fun givenBlizzardBestRuns(entity: WowEntity, vararg bestRuns: KeystoneBestRun) {
        `when`(blizzardClient.getMythicKeystoneProfile(entity.region, entity.realm, entity.name, season.id))
            .thenReturn(Either.Right(MythicKeystoneProfile(3000.0, bestRuns.toList())))
    }

    private fun synchronizer(repo: DataCacheRepository, seasonRepository: WowSeasonRepository) =
        WowEntitySynchronizer(repo, raiderIoClient, blizzardClient, seasonRepository)

    private fun buildResponseWithRuns(
        entity: WowEntity,
        bestRuns: List<MythicPlusRun> = listOf(),
        recentRuns: List<MythicPlusRun> = listOf()
    ) = WowCharacterProfile(
        entity.name, entity.realm, entity.region, "race", "faction", "class", "spec", 0.0,
        MythicPlusRank(1, 1, 1), MythicPlusRank(1, 1, 1), listOf(),
        bestRuns,
        recentRuns,
        0.0,
        null
    )

    private suspend fun getCachedData(dataCacheRepository: DataCacheRepository, entityId: Long): WowData =
        decodeJson.decodeFromString(
            dataCacheRepository.get(entityId).maxBy { it.inserted }.data
        )

    @Test
    fun `i can cache wow data`() {
        runBlocking {
            `when`(raiderIoClient.get(basicWowEntity)).thenReturn(RaiderIoMockHelper.get(basicWowEntity))
            `when`(raiderIoClient.get(basicWowEntity.copy(id = 2))).thenReturn(
                RaiderIoMockHelper.get(basicWowEntity.copy(id = 2))
            )
            `when`(raiderIoClient.cutoff(season.slug)).thenReturn(RaiderIoMockHelper.cutoff())
            givenBlizzardBestRuns(basicWowEntity)

            val repo = DataCacheInMemoryRepository().withState(listOf(wowDataCache))
            assertEquals(listOf(wowDataCache), repo.state())

            synchronizer(repo, seasonRepository())
                .synchronize(listOf(basicWowEntity, basicWowEntity.copy(id = 2)))
            assertEquals(3, repo.state().size)
        }
    }

    @Test
    fun `run details come from the blizzard best run of the same dungeon and key level`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun
            val lowerKeyDetails = RunDetails(runDetails.members.map { it.copy(itemLevel = 292) })

            `when`(raiderIoClient.cutoff(season.slug)).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity))
                .thenReturn(Either.Right(buildResponseWithRuns(basicWowEntity, listOf(run))))
            givenBlizzardBestRuns(
                basicWowEntity,
                KeystoneBestRun(run.dungeonId, run.keyLevel - 9, true, lowerKeyDetails),
                KeystoneBestRun(run.dungeonId, run.keyLevel, true, runDetails)
            )

            val repo = DataCacheInMemoryRepository()
            synchronizer(repo, seasonRepository()).synchronize(listOf(basicWowEntity))

            assertEquals(EnrichedMythicPlusRun(run, runDetails), getCachedData(repo, basicWowEntity.id).mythicPlusBestRuns.first())
        }
    }

    @Test
    fun `run details come from the timed blizzard best run when there is an untimed one of the same key level`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun
            val untimedDetails = RunDetails(runDetails.members.map { it.copy(itemLevel = 292) })

            `when`(raiderIoClient.cutoff(season.slug)).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity))
                .thenReturn(Either.Right(buildResponseWithRuns(basicWowEntity, listOf(run))))
            givenBlizzardBestRuns(
                basicWowEntity,
                KeystoneBestRun(run.dungeonId, run.keyLevel, false, untimedDetails),
                KeystoneBestRun(run.dungeonId, run.keyLevel, true, runDetails)
            )

            val repo = DataCacheInMemoryRepository()
            synchronizer(repo, seasonRepository()).synchronize(listOf(basicWowEntity))

            assertEquals(EnrichedMythicPlusRun(run, runDetails), getCachedData(repo, basicWowEntity.id).mythicPlusBestRuns.first())
        }
    }

    @Test
    fun `run details come from the untimed blizzard best run when the raiderio run is untimed`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun.copy(upgrades = 0)
            val timedDetails = RunDetails(runDetails.members.map { it.copy(itemLevel = 330) })

            `when`(raiderIoClient.cutoff(season.slug)).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity))
                .thenReturn(Either.Right(buildResponseWithRuns(basicWowEntity, listOf(run))))
            givenBlizzardBestRuns(
                basicWowEntity,
                KeystoneBestRun(run.dungeonId, run.keyLevel, true, timedDetails),
                KeystoneBestRun(run.dungeonId, run.keyLevel, false, runDetails)
            )

            val repo = DataCacheInMemoryRepository()
            synchronizer(repo, seasonRepository()).synchronize(listOf(basicWowEntity))

            assertEquals(EnrichedMythicPlusRun(run, runDetails), getCachedData(repo, basicWowEntity.id).mythicPlusBestRuns.first())
        }
    }

    @Test
    fun `data is still cached with null run details when blizzard fails`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun

            `when`(raiderIoClient.cutoff(season.slug)).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity))
                .thenReturn(Either.Right(buildResponseWithRuns(basicWowEntity, listOf(run))))
            `when`(blizzardClient.getMythicKeystoneProfile(basicWowEntity.region, basicWowEntity.realm, basicWowEntity.name, season.id))
                .thenReturn(Either.Left(HttpError(500, "Internal server error")))

            val repo = DataCacheInMemoryRepository()
            val errors = synchronizer(repo, seasonRepository()).synchronize(listOf(basicWowEntity))

            assertTrue(errors.isEmpty())
            assertNull(getCachedData(repo, basicWowEntity.id).mythicPlusBestRuns.first().details)
        }
    }

    @Test
    fun `run details are null when blizzard has no best run for that dungeon and key level`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun

            `when`(raiderIoClient.cutoff(season.slug)).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity))
                .thenReturn(Either.Right(buildResponseWithRuns(basicWowEntity, listOf(run))))
            givenBlizzardBestRuns(basicWowEntity, KeystoneBestRun(run.dungeonId + 1, run.keyLevel, true, runDetails))

            val repo = DataCacheInMemoryRepository()
            synchronizer(repo, seasonRepository()).synchronize(listOf(basicWowEntity))

            assertNull(getCachedData(repo, basicWowEntity.id).mythicPlusBestRuns.first().details)
        }
    }

    @Test
    fun `blizzard is called once per entity for all its best runs`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun
            val otherRun = run.copy(runId = run.runId + 1, dungeonId = run.dungeonId + 1)

            `when`(raiderIoClient.cutoff(season.slug)).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity))
                .thenReturn(Either.Right(buildResponseWithRuns(basicWowEntity, listOf(run, otherRun))))
            givenBlizzardBestRuns(
                basicWowEntity,
                KeystoneBestRun(run.dungeonId, run.keyLevel, true, runDetails),
                KeystoneBestRun(otherRun.dungeonId, otherRun.keyLevel, true, runDetails)
            )

            val repo = DataCacheInMemoryRepository()
            synchronizer(repo, seasonRepository()).synchronize(listOf(basicWowEntity))

            verify(blizzardClient, times(1))
                .getMythicKeystoneProfile(basicWowEntity.region, basicWowEntity.realm, basicWowEntity.name, season.id)
            assertEquals(
                listOf(runDetails, runDetails),
                getCachedData(repo, basicWowEntity.id).mythicPlusBestRuns.map { it.details }
            )
        }
    }

    @Test
    fun `data is cached with null run details when current season is unavailable`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun

            `when`(raiderIoClient.get(basicWowEntity))
                .thenReturn(Either.Right(buildResponseWithRuns(basicWowEntity, listOf(run))))

            val dataCacheRepository = DataCacheInMemoryRepository()
            val errors = synchronizer(dataCacheRepository, WowSeasonInMemoryRepository())
                .synchronize(listOf(basicWowEntity))

            assertTrue(errors.isEmpty())
            val cached = getCachedData(dataCacheRepository, basicWowEntity.id)
            assertNull(cached.quantile)
            assertNull(cached.mythicPlusBestRuns.first().details)
            verifyNoInteractions(blizzardClient)
        }
    }

    @Test
    fun `recent runs are stored without enrichment`() {
        runBlocking {
            val recentRun = RaiderIoHttpClientHelper.mythicPlusRun

            `when`(raiderIoClient.cutoff(season.slug)).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity))
                .thenReturn(Either.Right(buildResponseWithRuns(basicWowEntity, recentRuns = listOf(recentRun))))

            val dataCacheRepository = DataCacheInMemoryRepository()
            synchronizer(dataCacheRepository, seasonRepository())
                .synchronize(listOf(basicWowEntity))

            val cached = getCachedData(dataCacheRepository, basicWowEntity.id)
            assertEquals(recentRun, cached.mythicPlusRecentRuns.first())
        }
    }

    @Test
    fun `blizzard is not called when every best run already has details in the persistent cache`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun

            `when`(raiderIoClient.cutoff(season.slug)).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity))
                .thenReturn(Either.Right(buildResponseWithRuns(basicWowEntity, listOf(run))))
            givenBlizzardBestRuns(basicWowEntity, KeystoneBestRun(run.dungeonId, run.keyLevel, true, runDetails))

            val dataCacheRepository = DataCacheInMemoryRepository()
            synchronizer(dataCacheRepository, seasonRepository()).synchronize(listOf(basicWowEntity))
            synchronizer(dataCacheRepository, seasonRepository()).synchronize(listOf(basicWowEntity))

            verify(blizzardClient, times(1))
                .getMythicKeystoneProfile(basicWowEntity.region, basicWowEntity.realm, basicWowEntity.name, season.id)
            assertEquals(
                EnrichedMythicPlusRun(run, runDetails),
                getCachedData(dataCacheRepository, basicWowEntity.id).mythicPlusBestRuns.first()
            )
        }
    }

    @Test
    fun `blizzard is called again when a dungeon gets a new best run`() {
        runBlocking {
            val run = RaiderIoHttpClientHelper.mythicPlusRun
            val improvedRun = run.copy(runId = run.runId + 1, keyLevel = run.keyLevel + 2)
            val improvedRunDetails = RunDetails(runDetails.members.map { it.copy(itemLevel = 330) })

            `when`(raiderIoClient.cutoff(season.slug)).thenReturn(RaiderIoMockHelper.cutoff())
            `when`(raiderIoClient.get(basicWowEntity))
                .thenReturn(Either.Right(buildResponseWithRuns(basicWowEntity, listOf(run))))
                .thenReturn(Either.Right(buildResponseWithRuns(basicWowEntity, listOf(improvedRun))))
            givenBlizzardBestRuns(basicWowEntity, KeystoneBestRun(run.dungeonId, run.keyLevel, true, runDetails))

            val dataCacheRepository = DataCacheInMemoryRepository()
            synchronizer(dataCacheRepository, seasonRepository()).synchronize(listOf(basicWowEntity))

            givenBlizzardBestRuns(
                basicWowEntity,
                KeystoneBestRun(improvedRun.dungeonId, improvedRun.keyLevel, true, improvedRunDetails)
            )
            synchronizer(dataCacheRepository, seasonRepository()).synchronize(listOf(basicWowEntity))

            verify(blizzardClient, times(2))
                .getMythicKeystoneProfile(basicWowEntity.region, basicWowEntity.realm, basicWowEntity.name, season.id)
            assertEquals(
                EnrichedMythicPlusRun(improvedRun, improvedRunDetails),
                getCachedData(dataCacheRepository, basicWowEntity.id).mythicPlusBestRuns.first()
            )
        }
    }
}
