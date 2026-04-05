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
import com.kos.clients.raiderio.RaiderIoHttpClientHelper
import com.kos.datacache.RaiderIoMockHelper
import com.kos.entities.EntitiesTestHelper
import com.kos.eventsourcing.events.ViewCreatedEvent
import com.kos.eventsourcing.events.ViewEditedEvent
import com.kos.eventsourcing.events.ViewPatchedEvent
import com.kos.eventsourcing.events.ViewToBeCreatedEvent
import com.kos.sources.SyncGameCharactersTestCommon
import com.kos.sources.wow.staticdata.wowseason.WowSeason
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonsState
import com.kos.views.Game
import com.kos.views.ViewsTestHelper
import com.kos.views.ViewsTestHelper.owner
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class WowEntitySynchronizerTest : SyncGameCharactersTestCommon() {

    private val season = WowSeason(1, "Default Season", "default-season", 1, "", true)

    private suspend fun wowSeasonRepository() =
        WowSeasonInMemoryRepository().withState(WowSeasonsState(listOf(season)))

    @Test
    fun `wowEntitySynchronizer calls cache on VIEW_CREATED with WOW game`() = runBlocking {
        `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
        `when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity))
            .thenReturn(RaiderIoMockHelper.get(EntitiesTestHelper.basicWowEntity))

        val (charactersService, dataCacheRepository) = createService()

        val eventWithVersion = createEventWithVersion(
            ViewCreatedEvent(
                ViewsTestHelper.id,
                ViewsTestHelper.name,
                owner,
                listOf(EntitiesTestHelper.basicWowEntity.id),
                true,
                Game.WOW,
                ViewsTestHelper.featured,
                null
            ), Game.WOW
        )

        val service = WowEntitySynchronizer(dataCacheRepository, raiderIoClient, wowSeasonRepository())
        val spied = spyk(service)

        assertWowCacheInvocation(
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
    fun `wowEntitySynchronizer calls cache on VIEW_EDITED with WOW game`() = runBlocking {
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

        val service = WowEntitySynchronizer(dataCacheRepository, raiderIoClient, wowSeasonRepository())
        val spied = spyk(service)

        assertWowCacheInvocation(
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
    fun `wowEntitySynchronizer calls cache on VIEW_PATCHED with WOW game`() = runBlocking {
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

        val service = WowEntitySynchronizer(dataCacheRepository, raiderIoClient, wowSeasonRepository())
        val spied = spyk(service)

        assertWowCacheInvocation(
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

            val service = WowEntitySynchronizer(dataCacheRepository, raiderIoClient, wowSeasonRepository())
            val spied = spyk(service)

            assertWowCacheInvocation(
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

    @Test
    fun `run details are fetched and embedded when current season is available`() = runBlocking {
        val run = RaiderIoHttpClientHelper.mythicPlusRun
        val runDetails = RaiderIoHttpClientHelper.runDetails

        `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
        `when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity))
            .thenReturn(Either.Right(buildResponseWithRuns(EntitiesTestHelper.basicWowEntity, listOf(run))))
        `when`(raiderIoClient.getRunDetails(season.slug, run.runId.toString()))
            .thenReturn(Either.Right(runDetails))

        val (_, dataCacheRepository) = createService()
        WowEntitySynchronizer(dataCacheRepository, raiderIoClient, wowSeasonRepository())
            .synchronize(listOf(EntitiesTestHelper.basicWowEntity))

        val cached = getCachedData(dataCacheRepository, EntitiesTestHelper.basicWowEntity.id)
        assertEquals(EnrichedMythicPlusRun(run, runDetails), cached.mythicPlusBestRuns.first())
    }

    @Test
    fun `synchronization fails when there is no current season`() = runBlocking {
        `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())

        val (_, dataCacheRepository) = createService()
        val errors = WowEntitySynchronizer(dataCacheRepository, raiderIoClient, WowSeasonInMemoryRepository())
            .synchronize(listOf(EntitiesTestHelper.basicWowEntity))

        assertTrue(errors.isNotEmpty())
    }

    @Test
    fun `run details are null when getRunDetails fails`() = runBlocking {
        val run = RaiderIoHttpClientHelper.mythicPlusRun

        `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
        `when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity))
            .thenReturn(Either.Right(buildResponseWithRuns(EntitiesTestHelper.basicWowEntity, listOf(run))))
        `when`(raiderIoClient.getRunDetails(season.slug, run.runId.toString()))
            .thenReturn(Either.Left(HttpError(500, "Internal server error")))

        val (_, dataCacheRepository) = createService()
        val errors = WowEntitySynchronizer(dataCacheRepository, raiderIoClient, wowSeasonRepository())
            .synchronize(listOf(EntitiesTestHelper.basicWowEntity))

        assertTrue(errors.isNotEmpty())
        val cached = getCachedData(dataCacheRepository, EntitiesTestHelper.basicWowEntity.id)
        assertNull(cached.mythicPlusBestRuns.first().details)
    }

    @Test
    fun `getRunDetails is called only once for the same run id across multiple entities`(): Unit = runBlocking {
        val run = RaiderIoHttpClientHelper.mythicPlusRun
        val runDetails = RaiderIoHttpClientHelper.runDetails

        `when`(raiderIoClient.cutoff()).thenReturn(RaiderIoMockHelper.cutoff())
        `when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity))
            .thenReturn(Either.Right(buildResponseWithRuns(EntitiesTestHelper.basicWowEntity, listOf(run))))
        `when`(raiderIoClient.get(EntitiesTestHelper.basicWowEntity2))
            .thenReturn(Either.Right(buildResponseWithRuns(EntitiesTestHelper.basicWowEntity2, listOf(run))))
        `when`(raiderIoClient.getRunDetails(season.slug, run.runId.toString()))
            .thenReturn(Either.Right(runDetails))

        val (_, dataCacheRepository) = createService()
        WowEntitySynchronizer(dataCacheRepository, raiderIoClient, wowSeasonRepository())
            .synchronize(listOf(EntitiesTestHelper.basicWowEntity, EntitiesTestHelper.basicWowEntity2))

        verify(raiderIoClient, times(1)).getRunDetails(season.slug, run.runId.toString())
    }

    private fun buildResponseWithRuns(entity: com.kos.entities.domain.WowEntity, runs: List<MythicPlusRun>) =
        RaiderIoResponse(
            RaiderIoProfile(
                entity.name, entity.realm, entity.region, "class", "spec",
                listOf(MythicPlusSeasonScore("df-3", SeasonScores(0.0, 0.0, 0.0, 0.0, 0.0))),
                MythicPlusRanks(MythicPlusRank(1, 1, 1), MythicPlusRank(1, 1, 1), mapOf()),
                runs
            ),
            listOf()
        )

    private val decodeJson = Json { ignoreUnknownKeys = true }

    private suspend fun getCachedData(dataCacheRepository: com.kos.datacache.repository.DataCacheRepository, entityId: Long): RaiderIoData =
        decodeJson.decodeFromString(dataCacheRepository.get(entityId).first().data)

}
