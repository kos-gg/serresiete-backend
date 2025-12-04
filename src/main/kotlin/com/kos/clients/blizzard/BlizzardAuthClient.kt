package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.domain.TokenResponse
import com.kos.common.HttpError

interface BlizzardAuthClient {
    suspend fun getAccessToken(): Either<HttpError, TokenResponse>
}