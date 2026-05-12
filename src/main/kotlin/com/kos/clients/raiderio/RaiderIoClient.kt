package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.*
import com.kos.entities.domain.WowEntity
import com.kos.entities.domain.WowEntityRequest

interface RaiderIoClient {
    suspend fun get(wowEntity: WowEntity): Either<ClientError, RaiderIoResponse>
    suspend fun getExpansionSeasons(expansionId: Int): Either<ClientError, ExpansionSeasons>
    suspend fun getRunDetails(season: String, runId: String): Either<ClientError, RunDetails>

    suspend fun exists(wowEntityRequest: WowEntityRequest): Boolean
    suspend fun cutoff(seasonSlug: String): Either<ClientError, RaiderIoCutoff>
    suspend fun wowheadEmbeddedCalculator(wowEntity: WowEntity): Either<ClientError, RaiderioWowHeadEmbeddedResponse>
}