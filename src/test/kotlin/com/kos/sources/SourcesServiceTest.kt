package com.kos.sources

import com.kos.clients.domain.Season
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
            val seasonData = """
                {
                  "is_main_season": true,
                  "name": "Mythic+ Season 3",
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
            val season = Json.decodeFromString<Season>(seasonData)
            `when`(wowSeasonsService.getWowCurrentSeason()).thenReturn(season)
            val service = SourcesService(wowSeasonsService)
            assertEquals(season, service.getWowCurrentSeason())
        }
    }

}