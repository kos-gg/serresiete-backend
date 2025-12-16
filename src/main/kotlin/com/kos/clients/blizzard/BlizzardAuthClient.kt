package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.TokenResponse
import com.kos.common.error.HttpError

interface BlizzardAuthClient {
    suspend fun getAccessToken(): Either<HttpError, TokenResponse>
    suspend fun getAccessTokenV2(): Either<ClientError, TokenResponse>
}