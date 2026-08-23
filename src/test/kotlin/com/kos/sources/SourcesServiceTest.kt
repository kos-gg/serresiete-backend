package com.kos.sources

import com.kos.clients.domain.Season
import com.kos.clients.raiderio.RaiderIoHttpClientHelper
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`
import kotlin.test.Test
import kotlin.test.assertEquals

class SourcesServiceTest {
    private val wowSeasonsService = mock(WowSeasonService::class.java)

    @Test
    fun `it should return current wow season`() {
        runBlocking {
            val seasonData = RaiderIoHttpClientHelper.ResourceLoader.readResource("unit/wow/season-response.json")
            val season = Json.decodeFromString<Season>(seasonData)
            `when`(wowSeasonsService.getWowCurrentSeason()).thenReturn(season)
            val service = SourcesService(wowSeasonsService)
            assertEquals(season, service.getWowCurrentSeason())
        }
    }
}
