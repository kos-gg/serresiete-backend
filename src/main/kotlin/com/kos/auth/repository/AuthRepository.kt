package com.kos.auth.repository

import arrow.core.Either
import com.kos.auth.Authorization
import com.kos.common.WithState
import com.kos.common.error.RepositoryError

//TODO: This should disappear at some point. We should self-contain auth in jwt token.
interface AuthRepository : WithState<List<Authorization>, AuthRepository> {
    suspend fun insertToken(userName: String, token: String, isAccess: Boolean): Either<RepositoryError, Authorization?>
    suspend fun deleteTokensFromUser(userName: String): Boolean
    suspend fun getAuthorization(token: String): Authorization?
    suspend fun deleteExpiredTokens(): Int
}