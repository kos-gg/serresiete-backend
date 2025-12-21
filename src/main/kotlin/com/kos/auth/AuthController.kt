package com.kos.auth

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.error.AuthenticationError
import com.kos.common.error.ControllerError
import com.kos.common.error.NotAuthorized
import com.kos.common.error.NotEnoughPermissions

class AuthController(
    private val authService: AuthService,
) {
    suspend fun login(client: String?): Either<ControllerError, LoginResponse> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> authService.login(client)
                .mapLeft {
                    AuthenticationError(it.error())
                }
        }
    }

    suspend fun logout(client: String?, activities: Set<Activity>): Either<ControllerError, Boolean> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else ->
                if (activities.contains(Activities.logout)) Either.Right(authService.logout(client))
                else Either.Left(NotEnoughPermissions(client))
        }
    }

    suspend fun refresh(userName: String?): Either<ControllerError, LoginResponse?> {
        return when (userName) {
            null -> Either.Left(NotAuthorized)
            else -> authService.refresh(userName)
                .mapLeft {
                    AuthenticationError(it.error())
                }
        }
    }
}