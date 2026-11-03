package com.kos.sources.wow.staticdata.wowseason

import arrow.core.Either
import com.kos.clients.HttpError
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.raiderio.RaiderIoHttpClientHelper
import com.kos.sources.wow.staticdata.wowexpansion.WowExpansion
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionInMemoryRepository
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionState
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonInMemoryRepository
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonsState
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.junit.jupiter.api.Test
import org.mockito.Mockito
import org.mockito.Mockito.`when`
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WowSeasonServiceTest {
    private val raiderIoClient = Mockito.mock(RaiderIoClient::class.java)


    @Test
    fun `i can add a new mythic plus dungeon season given there is no season data for the current expansion`() {
        runBlocking {
            val expectedExpansionSeasons = listOf(Season(true, "TWW3", "tww-3", 15, listOf()))
            `when`(raiderIoClient.getExpansionSeasons(10))
                .thenReturn(Either.Right(expectedExpansionSeasons))

            val wowExpansionState = WowExpansionState(listOf(WowExpansion(10, "TWW", true)))
            val wowSeasonsState = WowSeasonsState(listOf())
            val seasonService = createService(wowExpansionState, wowSeasonsState)
            val season = seasonService.addNewMythicPlusSeason()

            assertEquals(expectedExpansionSeasons[0].blizzardSeasonId, season.id)
            assertEquals(expectedExpansionSeasons[0].name, season.name)
        }
    }

    @Test
    fun `i can not add a new mythic plus dungeon season given in repository it already exists the same current season`() {
        runBlocking {
            val expectedExpansionSeasons =
                listOf(Season(true, "TWW Season 3", "tww-season-3", 15, listOf()))
            `when`(raiderIoClient.getExpansionSeasons(10))
                .thenReturn(Either.Right(expectedExpansionSeasons))

            val wowExpansionState = WowExpansionState(listOf(WowExpansion(10, "TWW", true)))
            val wowSeasonsState = WowSeasonsState(listOf(WowSeason(15, "TWW Season 3", "tww-season-3", 10, "", true)))
            val seasonService = createService(wowExpansionState, wowSeasonsState)

            val error = assertFailsWith<IllegalStateException> { seasonService.addNewMythicPlusSeason() }

            assertEquals("Season TWW Season 3 (15) already exists", error.message)
        }
    }

    @Test
    fun `i can not add a new mythic plus dungeon season because the raider io client is not available`() {
        runBlocking {
            `when`(raiderIoClient.getExpansionSeasons(10))
                .thenReturn(Either.Left(HttpError(500, "Internal server error")))

            val wowExpansionState = WowExpansionState(listOf(WowExpansion(10, "TWW", true)))
            val wowSeasonsState = WowSeasonsState(listOf(WowSeason(15, "TWW Season 3", "tww-season-3", 10, "", true)))
            val seasonService = createService(wowExpansionState, wowSeasonsState)

            val error = assertFailsWith<IllegalStateException> { seasonService.addNewMythicPlusSeason() }

            assertEquals("Third party call failed: HttpError(status=500, body=Internal server error)", error.message)
        }
    }

    @Test
    fun `i can not add a new mythic plus dungeon season because there is not current season for the current expansion`() {
        runBlocking {
            val expectedExpansionSeasons =
                listOf(Season(false, "TWW Season 3", "tww-season-3", 15, listOf()))
            `when`(raiderIoClient.getExpansionSeasons(10))
                .thenReturn(Either.Right(expectedExpansionSeasons))

            val wowExpansionState = WowExpansionState(listOf(WowExpansion(10, "TWW", true)))
            val wowSeasonsState = WowSeasonsState(listOf(WowSeason(15, "TWW Season 3", "tww-season-3", 10, "", true)))
            val seasonService = createService(wowExpansionState, wowSeasonsState)

            val error = assertFailsWith<IllegalStateException> { seasonService.addNewMythicPlusSeason() }

            assertEquals("There is no current season for expansion 10 - TWW", error.message)
        }
    }

    @Test
    fun `i can retrieve wow current season`() {
        runBlocking {
            val wowExpansionState = WowExpansionState(listOf(WowExpansion(10, "TWW", true)))
            val seasonData = RaiderIoHttpClientHelper.ResourceLoader.readResource("unit/wow/season-response.json")
            val season = WowSeason(15, "TWW Season 3", "tww-season-3", 10, seasonData, true)
            val wowSeasonsState = WowSeasonsState(listOf(season))
            val service = createService(wowExpansionState, wowSeasonsState)
            assertEquals(Json.decodeFromString<Season>(seasonData), service.getWowCurrentSeason())
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

        return WowSeasonService(staticDataInMemoryRepository, seasonInMemoryRepository, raiderIoClient)
    }

}