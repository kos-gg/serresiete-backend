package com.kos.sources.wow.staticdata

import com.kos.clients.domain.Season
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonRepository
import kotlinx.serialization.json.Json

class WowStaticDataService(val wowSeasonRepository: WowSeasonRepository) {
    suspend fun getWowStaticData(): Season? =
        wowSeasonRepository.getCurrentSeason()?.let { Json.decodeFromString<Season>(it.seasonData) }
}