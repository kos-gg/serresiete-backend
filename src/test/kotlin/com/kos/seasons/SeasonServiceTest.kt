package com.kos.seasons

import arrow.core.Either
import com.kos.clients.HttpError
import com.kos.clients.domain.ExpansionSeasons
import com.kos.clients.domain.Season
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.RetryConfig
import com.kos.sources.wow.staticdata.wowexpansion.WowExpansion
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionInMemoryRepository
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionState
import com.kos.sources.wow.staticdata.wowseason.WowSeason
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonsState
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlin.test.fail

class SeasonServiceTest {
    private val raiderIoClient = Mockito.mock(RaiderIoClient::class.java)
    private val retryConfig = RetryConfig(1, 1000)


    @Test
    fun `i can add a new mythic plus dungeon season given there is no season data for the current expansion`() {
        runBlocking {
            val expectedExpansionSeasons = ExpansionSeasons(listOf(Season(true, "TWW3", 15, listOf())))
            `when`(raiderIoClient.getExpansionSeasons(10))
                .thenReturn(Either.Right(expectedExpansionSeasons))

            val wowExpansionState = WowExpansionState(listOf(WowExpansion(10, "TWW", true)))
            val wowSeasonsState = WowSeasonsState(listOf())
            val seasonService = createService(wowExpansionState, wowSeasonsState)
            seasonService.addNewMythicPlusSeason()
                .onLeft { fail() }
                .onRight {
                    assertEquals(expectedExpansionSeasons.seasons[0].blizzardSeasonId, it.id)
                    assertEquals(expectedExpansionSeasons.seasons[0].name, it.name)
                }
        }
    }

    @Test
    fun `i can not add a new mythic plus dungeon season given in repository it already exists the same current season`() {
        runBlocking {
            val expectedExpansionSeasons = ExpansionSeasons(listOf(Season(true, "TWW Season 3", 15, listOf())))
            `when`(raiderIoClient.getExpansionSeasons(10))
                .thenReturn(Either.Right(expectedExpansionSeasons))

            val wowExpansionState = WowExpansionState(listOf(WowExpansion(10, "TWW", true)))
            val wowSeasonsState = WowSeasonsState(listOf(WowSeason(15, "TWW Season 3", 10, "")))
            val seasonService = createService(wowExpansionState, wowSeasonsState)

            assertTrue(
                seasonService.addNewMythicPlusSeason()
                    .isLeft()
            )
        }
    }

    @Test
    fun `i can not add a new mythic plus dungeon season because the raider io client is not available`() {
        runBlocking {
            `when`(raiderIoClient.getExpansionSeasons(10))
                .thenReturn(Either.Left(HttpError(500, "Internal server error")))

            val wowExpansionState = WowExpansionState(listOf(WowExpansion(10, "TWW", true)))
            val wowSeasonsState = WowSeasonsState(listOf(WowSeason(15, "TWW Season 3", 10, "")))
            val seasonService = createService(wowExpansionState, wowSeasonsState)

            assertTrue(
                seasonService.addNewMythicPlusSeason()
                    .isLeft()
            )
        }
    }

    @Test
    fun `i can not add a new mythic plus dungeon season because there is not current season for the current expansion`() {
        runBlocking {
            val expectedExpansionSeasons = ExpansionSeasons(listOf(Season(false, "TWW Season 3", 15, listOf())))
            `when`(raiderIoClient.getExpansionSeasons(10))
                .thenReturn(Either.Right(expectedExpansionSeasons))

            val wowExpansionState = WowExpansionState(listOf(WowExpansion(10, "TWW", true)))
            val wowSeasonsState = WowSeasonsState(listOf(WowSeason(15, "TWW Season 3", 10, "")))
            val seasonService = createService(wowExpansionState, wowSeasonsState)

            assertTrue(
                seasonService.addNewMythicPlusSeason()
                    .isLeft()
            )
        }
    }

    private suspend fun createService(
        expansionsState: WowExpansionState,
        seasonsState: WowSeasonsState
    ): WowSeasonService {
        val staticDataInMemoryRepository =
            WowExpansionInMemoryRepository()
                .withState(expansionsState)
        val seasonInMemoryRepository = WowSeasonInMemoryRepository()
            .withState(seasonsState)

        return WowSeasonService(staticDataInMemoryRepository, seasonInMemoryRepository, raiderIoClient, retryConfig)
    }

}