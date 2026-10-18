package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.datacache.domain.RunDetails
import com.kos.entities.domain.WowEntity
import com.kos.entities.domain.WowEntityRequest
import com.kos.sources.wow.staticdata.wowseason.Season
import com.kos.sources.wow.SeasonCutoff
import com.kos.sources.wow.WowCharacterProfile


interface RaiderIoClient {
    suspend fun get(wowEntity: WowEntity): Either<ClientError, WowCharacterProfile>
    suspend fun getExpansionSeasons(expansionId: Int): Either<ClientError, List<Season>>
    suspend fun getRunDetails(season: String, runId: String): Either<ClientError, RunDetails>
    suspend fun getScore(wowEntityRequest: WowEntityRequest): Either<ClientError, Double>
    suspend fun cutoff(seasonSlug: String): Either<ClientError, SeasonCutoff>
    suspend fun wowheadEmbeddedCalculator(
        region: String,
        realm: String,
        name: String
    ): Either<ClientError, String>
}