package com.kos.auth.repository

import arrow.core.Either
import com.kos.auth.Authorization
import com.kos.common.InMemoryRepository
import com.kos.common.error.InsertError
import java.time.OffsetDateTime

class AuthInMemoryRepository : AuthRepository, InMemoryRepository {

    private val daysBeforeAccessTokenExpires: Long = 1
    private val daysBeforeRefreshTokenExpires: Long = 30
    private val authorizations = mutableListOf<Authorization>()

    override suspend fun insertToken(userName: String, token: String, isAccess: Boolean): Either<InsertError, Authorization?> {
        return if(authorizations.map { it.token }.contains(token)) Either.Left(InsertError("Error inserting token $token"))
        else {
            val authorization = Authorization(
                userName, token, OffsetDateTime.now(), OffsetDateTime.now().plusDays(
                    if (isAccess) daysBeforeAccessTokenExpires else daysBeforeRefreshTokenExpires
                ), isAccess
            )
            authorizations.add(authorization)
            Either.Right(authorization)
        }
    }

    override suspend fun deleteTokensFromUser(userName: String) = authorizations.removeIf { it.userName == userName }
    override suspend fun getAuthorization(token: String): Authorization? {
        return authorizations.find { it.token == token }
    }

    override suspend fun deleteExpiredTokens(): Int {
        val currentTime = OffsetDateTime.now()
        val deletedTokens = authorizations.count { it.validUntil != null && it.validUntil < currentTime }

        authorizations.removeAll { it.validUntil != null && it.validUntil < currentTime }

        return deletedTokens
    }

    override suspend fun state(): List<Authorization> {
        return authorizations
    }

    override suspend fun withState(initialState: List<Authorization>): AuthInMemoryRepository {
        authorizations.addAll(initialState)
        return this
    }

    override fun clear() {
        authorizations.clear()
    }

}