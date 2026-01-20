package com.kos.common.error

sealed class AuthError(val message: String)

data class JWTCreationError(val error: String) : AuthError(error)

fun AuthError.toServiceError(message: String): ServiceError =
    when (this) {
        is JWTCreationError -> AuthTokenError(message)
    }
