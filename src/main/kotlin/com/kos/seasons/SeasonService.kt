package com.kos.seasons

import arrow.core.Either
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.HttpError
import com.kos.common.Retry.retryEitherWithFixedDelay
import com.kos.common.RetryConfig
import com.kos.common.UnableToAddNewMythicPlusSeason
import com.kos.common.WithLogger
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

    suspend fun addNewMythicPlusSeason(): Either<HttpError, GameSeason> {
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
                        10, //TODO: SUS HARDCODE
                        currentSeason
                    )

                seasonRepository.insert(wowSeason).fold(
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
        return staticDataRepository.getExpansions()
            .firstOrNull { it.isCurrentExpansion }
            ?: throw UnableToAddNewMythicPlusSeason("No current expansion found")
    }
}