package com.kos.sources

import com.kos.sources.wow.staticdata.wowseason.Season
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService

class SourcesService(
    val wowSeasonService: WowSeasonService
) {
    suspend fun getWowCurrentSeason(): Season? = wowSeasonService.getWowCurrentSeason()
}
