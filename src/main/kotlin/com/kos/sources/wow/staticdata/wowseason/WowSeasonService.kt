package com.kos.sources.wow.staticdata.wowseason

import arrow.core.Either
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.HttpError
import com.kos.common.Retry.retryEitherWithFixedDelay
import com.kos.common.RetryConfig
import com.kos.common.UnableToAddNewMythicPlusSeason
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
    private val retryConfig: RetryConfig,
) : WithLogger("SeasonService") {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    suspend fun addNewMythicPlusSeason(): Either<HttpError, WowSeason> {
        val currentExpansion = getCurrentExpansion()

        return retryEitherWithFixedDelay(retryConfig, "raiderIoGetExpansionSeasons") {
            raiderIoClient.getExpansionSeasons(currentExpansion.id)
        }.fold(
            ifLeft = { error ->
                logger.error("Failed to fetch expansion seasons: $error")
                Either.Left(error)
            },
            ifRight = { expansionSeasons ->
                val currentSeason = expansionSeasons.seasons
                    .firstOrNull { it.isCurrentSeason }
                    ?: return Either.Left(
                        UnableToAddNewMythicPlusSeason(
                            "There is no current season for expansion ${currentExpansion.id} - ${currentExpansion.name}"
                        )
                    )

                val wowSeason =
                    WowSeason(
                        currentSeason.blizzardSeasonId,
                        currentSeason.name,
                        10,
                        json.encodeToString(currentSeason)
                    )

                wowSeasonRepository.insert(wowSeason).fold(
                    {
                        Either.Left(UnableToAddNewMythicPlusSeason(it.message))
                    },
                    {
                        Either.Right(wowSeason)
                    }
                )
            }
        )
    }

    private suspend fun getCurrentExpansion(): WowExpansion {
        return wowExpansionRepository.getExpansions()
            .firstOrNull { it.isCurrentExpansion }
            ?: throw UnableToAddNewMythicPlusSeason("No current expansion found") //TODO: NOT THROW EXCEPTION, CARRY ERROR TO CONTROLLER
    }
}