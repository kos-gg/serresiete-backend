package com.kos.auth

import com.kos.common.error.AuthError
import com.kos.common.OffsetDateTimeSerializer
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class Authorization(
    val userName: String,
    val token: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val lastUsed: OffsetDateTime,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val validUntil: OffsetDateTime?,
    val isAccess: Boolean
)

@Serializable
data class LoginResponse(
    val accessToken: String?,
    val refreshToken: String?
)

data class JWTCreationError(override val message: String) : AuthError
data class InsertAuthToken(override val message: String): AuthError

enum class TokenMode {
    ACCESS {
        override fun toString(): String = "access"
    },
    REFRESH {
        override fun toString(): String = "refresh"
    };

    companion object {
        fun fromString(str: String): TokenMode =
            when (str) {
                "access" -> ACCESS
                "refresh" -> REFRESH
                else -> throw IllegalArgumentException("Illegal tokenMode: $str")
            }
    }
}