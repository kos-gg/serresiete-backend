package com.kos.seasons

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.Retry.retryEitherWithFixedDelay
import com.kos.common.RetryConfig
import com.kos.common.WithLogger
import com.kos.common.error.ServiceError
import com.kos.common.error.UnableToAddNewMythicPlusSeason
import com.kos.seasons.repository.SeasonRepository
import com.kos.staticdata.WowExpansion
import com.kos.staticdata.repository.StaticDataRepository
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class SeasonService(
    private val staticDataRepository: StaticDataRepository,
    private val seasonRepository: SeasonRepository,
    private val raiderIoClient: RaiderIoClient,
    private val retryConfig: RetryConfig,
) : WithLogger("SeasonService") {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun addNewMythicPlusSeason(): Either<ServiceError, GameSeason> =
        either {
            val currentExpansion = getCurrentExpansion()

            val expansionId = currentExpansion?.id
                ?: raise(
                    UnableToAddNewMythicPlusSeason(
                        "Couldn't find current expansion from database"
                    )
                )

            val expansionSeasons =
                retryEitherWithFixedDelay(retryConfig, "raiderIoGetExpansionSeasons") {
                    raiderIoClient.getExpansionSeasons(expansionId)
                }.mapLeft {
                    logger.error("Failed to fetch expansion seasons: $it")
                    it.toSyncProcessingError("GetWowExpansions")
                }.bind()

            val currentSeason = expansionSeasons.seasons
                .firstOrNull { it.isCurrentSeason }
                ?: raise(
                    UnableToAddNewMythicPlusSeason(
                        "There is no current season for expansion ${expansionId} - ${currentExpansion.name}"
                    )
                )

            val wowSeason = WowSeason(
                currentSeason.blizzardSeasonId,
                currentSeason.name,
                10,
                json.encodeToString(currentSeason)
            )

            seasonRepository.insert(wowSeason)
                .mapLeft { UnableToAddNewMythicPlusSeason(it.message) }
                .bind()

            wowSeason
        }


    private suspend fun getCurrentExpansion(): WowExpansion? {
        return staticDataRepository.getExpansions()
            .firstOrNull { it.isCurrentExpansion }
    }
}