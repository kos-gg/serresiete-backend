package com.kos.clients.raiderio

import arrow.core.Either
import com.kos.clients.domain.RaiderIoCutoff
import com.kos.clients.domain.RaiderIoResponse
import com.kos.clients.domain.RaiderioWowHeadEmbeddedResponse
import com.kos.common.HttpError
import com.kos.entities.WowEntity
import com.kos.entities.WowEntityRequest

interface RaiderIoClient {
    suspend fun get(wowEntity: WowEntity): Either<HttpError, RaiderIoResponse>

    suspend fun exists(wowEntityRequest: WowEntityRequest): Boolean

    suspend fun cutoff(): Either<HttpError, RaiderIoCutoff>
    suspend fun wowheadEmbeddedCalculator(wowEntity: WowEntity): Either<HttpError, RaiderioWowHeadEmbeddedResponse>
}