package com.kos.sources

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.clients.domain.LoggedDetails
import com.kos.clients.domain.RunDetails
import com.kos.clients.domain.RunDetailsCharacter
import com.kos.clients.domain.RunDetailsCharacterClass
import com.kos.clients.domain.RunDetailsCharacterRealm
import com.kos.clients.domain.RunDetailsCharacterSpec
import com.kos.clients.domain.RunDetailsResponse
import com.kos.clients.domain.RunDetailsRosterEntry
import com.kos.clients.domain.Season
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import com.kos.common.error.SyncProcessingError
import com.kos.common.error.ViewDataError
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SourcesControllerTest {
    private val sourcesService = mock(SourcesService::class.java)
    private val seasonData = """
                {
                  "is_main_season": true,
                  "name": "Mythic+ Season 3",
                  "slug": "mythic-plus-season-3",
                  "blizzard_season_id": 12,
                  "dungeons": [
                    {
                      "name": "The Nokhud Offensive",
                      "short_name": "NO",
                      "challenge_mode_id": 2516
                    },
                    {
                      "name": "Algeth'ar Academy",
                      "short_name": "AA",
                      "challenge_mode_id": 2520
                    },
                    {
                      "name": "Ruby Life Pools",
                      "short_name": "RLP",
                      "challenge_mode_id": 2521
                    }
                  ]
                }
            """.trimIndent()
    private val season = Json.decodeFromString<Season>(seasonData)

    @Test
    fun `it should return forbidden when no activities sent`() {
        runBlocking {
            val controller = SourcesController(sourcesService)
            val client = "owner"
            val result = controller.getWowStaticData(client, setOf())
            result
                .onRight { fail("expected forbidden exception") }
                .onLeft { assertEquals(NotEnoughPermissions(client), it) }
        }
    }

    @Test
    fun `it should return not authorized when no client sent`() {
        runBlocking {
            val controller = SourcesController(sourcesService)
            val result = controller.getWowStaticData(null, setOf())
            result
                .onRight { fail("expected not authorized exception") }
                .onLeft { assertEquals(NotAuthorized, it) }
        }
    }

    @Test
    fun `it should return wow static data`() {
        runBlocking {
            `when`(sourcesService.getWowCurrentSeason()).thenReturn(season)
            val controller = SourcesController(sourcesService)
            val client = "owner"
            val result = controller.getWowStaticData(client, setOf(Activities.getWowStaticData))
            result
                .onRight { assertEquals(season, it) }
                .onLeft { fail(it.toString()) }
        }
    }

    private val roster = listOf(
        RunDetailsRosterEntry(RunDetailsCharacter("Nareez", RunDetailsCharacterClass("Warlock"), RunDetailsCharacterSpec("Affliction"), RunDetailsCharacterRealm("Blackrock")))
    )
    private val runDetails = RunDetails(roster, LoggedDetails(emptyList()))
    private val runDetailsResponse = RunDetailsResponse(roster, 0)

    @Test
    fun `getRunDetails should return not authorized when no client sent`() {
        runBlocking {
            val controller = SourcesController(sourcesService)
            val result = controller.getRunDetails(null, setOf(), "3415343")
            result
                .onRight { fail("expected not authorized exception") }
                .onLeft { assertEquals(NotAuthorized, it) }
        }
    }

    @Test
    fun `getRunDetails should return not enough permissions when activity is missing`() {
        runBlocking {
            val controller = SourcesController(sourcesService)
            val client = "owner"
            val result = controller.getRunDetails(client, setOf(), "3415343")
            result
                .onRight { fail("expected forbidden exception") }
                .onLeft { assertEquals(NotEnoughPermissions(client), it) }
        }
    }

    @Test
    fun `getRunDetails should return run details`() {
        runBlocking {
            `when`(sourcesService.getRunDetails("3415343")).thenReturn(Either.Right(runDetails))
            val controller = SourcesController(sourcesService)
            val result = controller.getRunDetails("owner", setOf(Activities.getWowRunDetails), "3415343")
            result
                .onRight { assertEquals(runDetailsResponse, it) }
                .onLeft { fail(it.toString()) }
        }
    }

    @Test
    fun `getRunDetails should return error when service fails`() {
        runBlocking {
            `when`(sourcesService.getRunDetails("3415343")).thenReturn(Either.Left(SyncProcessingError("GET_RUN_DETAILS", "No current season found")))
            val controller = SourcesController(sourcesService)
            val result = controller.getRunDetails("owner", setOf(Activities.getWowRunDetails), "3415343")
            result
                .onRight { fail("expected error") }
                .onLeft { assertEquals(ViewDataError("GET_RUN_DETAILS: No current season found"), it) }
        }
    }
}