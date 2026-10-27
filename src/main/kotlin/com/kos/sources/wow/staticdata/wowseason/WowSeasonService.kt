package com.kos.sources.wow.staticdata.wowseason

import com.kos.clients.orThrow
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.WithLogger
import com.kos.sources.wow.staticdata.wowexpansion.WowExpansion
import com.kos.sources.wow.staticdata.wowexpansion.repository.WowExpansionRepository
import com.kos.sources.wow.staticdata.wowseason.repository.WowSeasonRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class WowSeasonService(
    private val wowExpansionRepository: WowExpansionRepository,
    private val wowSeasonRepository: WowSeasonRepository,
    private val raiderIoClient: RaiderIoClient,
) : WithLogger("SeasonService") {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun addNewMythicPlusSeason(): WowSeason {
        val currentExpansion = checkNotNull(getCurrentExpansion()) { "Couldn't find current expansion from database" }

        val currentSeason = raiderIoClient.getExpansionSeasons(currentExpansion.id)
            .orThrow()
            .firstOrNull { it.isCurrentSeason }
            ?: error("There is no current season for expansion ${currentExpansion.id} - ${currentExpansion.name}")

        val wowSeason = WowSeason(
            currentSeason.blizzardSeasonId,
            currentSeason.name,
            currentSeason.slug,
            10,
            json.encodeToString(currentSeason),
            currentSeason.isCurrentSeason
        )

        check(wowSeasonRepository.insert(wowSeason)) { "Season ${wowSeason.name} (${wowSeason.id}) already exists" }

        return wowSeason
    }

    suspend fun getWowCurrentSeason(): Season? =
        wowSeasonRepository.getCurrentSeason()?.let { Json.decodeFromString<Season>(it.seasonData) }

    private suspend fun getCurrentExpansion(): WowExpansion? {
        return wowExpansionRepository.getExpansions()
            .firstOrNull { it.isCurrentExpansion }
    }
}