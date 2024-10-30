package com.kos.auth

import arrow.core.Either
import com.kos.activities.Activities
import com.kos.activities.Activity
import com.kos.common.ControllerError
import com.kos.common.NotAuthorized
import com.kos.common.NotEnoughPermissions

class AuthController(
    private val authService: AuthService,
) {
    suspend fun login(client: String?): Either<ControllerError, LoginResponse> {
        return when (client) {
            null -> Either.Left(NotAuthorized)
            else -> authService.login(client)
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

    suspend fun refresh(refreshToken: String?): Either<ControllerError, Authorization?> {
        return when (refreshToken) {
            null -> Either.Left(NotAuthorized)
            else -> authService.refresh(refreshToken)
        }
    }
}