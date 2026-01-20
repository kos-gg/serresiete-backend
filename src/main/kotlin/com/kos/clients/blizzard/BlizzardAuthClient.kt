package com.kos.clients.blizzard

import arrow.core.Either
import com.kos.clients.ClientError
import com.kos.clients.domain.TokenResponse

interface BlizzardAuthClient {
    suspend fun getAccessToken(): Either<ClientError, TokenResponse>
}