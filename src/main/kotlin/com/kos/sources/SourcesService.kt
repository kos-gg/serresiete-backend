package com.kos.sources

import arrow.core.Either
import com.kos.clients.domain.RunDetails
import com.kos.clients.domain.Season
import com.kos.common.error.ServiceError
import com.kos.sources.wow.staticdata.wowseason.WowSeasonService

class SourcesService(
    val wowSeasonService: WowSeasonService
) {
    suspend fun getWowCurrentSeason(): Season? = wowSeasonService.getWowCurrentSeason()
    suspend fun getRunDetails(runId: String): Either<ServiceError, RunDetails> = wowSeasonService.getRunDetails(runId)
}