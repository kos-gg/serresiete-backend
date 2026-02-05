package com.kos.sources

import com.kos.clients.domain.Season
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService

class SourcesService(
    val wowSeasonService: WowSeasonService
) {
    suspend fun getWowCurrentSeason(): Season? = wowSeasonService.getWowCurrentSeason()
}