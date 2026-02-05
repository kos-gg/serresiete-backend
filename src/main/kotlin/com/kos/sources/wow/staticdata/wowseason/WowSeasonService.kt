package com.kos.sources.wow.staticdata.wowseason

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.domain.Season
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.error.UnableToAddNewMythicPlusSeason
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

    suspend fun addNewMythicPlusSeason(): Either<ServiceError, WowSeason> =
        either {
            val currentExpansion = getCurrentExpansion()

            val expansionId = currentExpansion?.id
                ?: raise(
                    UnableToAddNewMythicPlusSeason(
                        "Couldn't find current expansion from database"
                    )
                )

            val expansionSeasons =
                raiderIoClient.getExpansionSeasons(expansionId)
                    .mapLeft {
                        logger.error("Failed to fetch expansion seasons: $it")
                        it.toSyncProcessingError("GetWowExpansions")
                    }.bind()

            val currentSeason = expansionSeasons.seasons
                .firstOrNull { it.isCurrentSeason }
                ?: raise(
                    UnableToAddNewMythicPlusSeason(
                        "There is no current season for expansion $expansionId - ${currentExpansion.name}"
                    )
                )

            val wowSeason = WowSeason(
                currentSeason.blizzardSeasonId,
                currentSeason.name,
                10,
                json.encodeToString(currentSeason),
                currentSeason.isCurrentSeason
            )

            wowSeasonRepository.insert(wowSeason)
                .mapLeft { UnableToAddNewMythicPlusSeason(it.message) }
                .bind()

            wowSeason
        }

    suspend fun getWowCurrentSeason(): Season? =
        wowSeasonRepository.getCurrentSeason()?.let { Json.decodeFromString<Season>(it.seasonData) }


    private suspend fun getCurrentExpansion(): WowExpansion? {
        return wowExpansionRepository.getExpansions()
            .firstOrNull { it.isCurrentExpansion }
    }
}