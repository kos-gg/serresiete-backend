package com.kos.sources

import com.kos.activities.Activities
import com.kos.clients.domain.Season
import com.kos.clients.raiderio.RaiderIoHttpClientHelper
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.fail

class SourcesControllerTest {
    private val sourcesService = mock(SourcesService::class.java)
    private val seasonData = RaiderIoHttpClientHelper.ResourceLoader.readResource("unit/wow/season-response.json")
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
}
