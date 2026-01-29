package com.kos.sources

import com.kos.clients.domain.Season
import com.kos.sources.wow.staticdata.WowStaticDataService

class SourcesService(
    val wowStaticDataService: WowStaticDataService
) {
    suspend fun getWowStaticData(): Season? = wowStaticDataService.getWowStaticData()
}