package com.kos.auth

import arrow.core.Either
import arrow.core.raise.either
import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTCreationException
import com.kos.activities.Activity
import com.kos.auth.repository.AuthRepository
import com.kos.common.error.ControllerError
import com.kos.common.JWTConfig
import com.kos.credentials.CredentialsService
import com.kos.roles.Role
import com.kos.roles.RolesService
import java.time.OffsetDateTime

class AuthService(
    private val authRepository: AuthRepository,
    private val credentialsService: CredentialsService,
    private val rolesService: RolesService,
    private val jwtConfig: JWTConfig
) {

    suspend fun login(userName: String): Either<ControllerError, LoginResponse> {

        return if (credentialsService.getUserRoles(userName).contains(Role.SERVICE)) {
            either {
                LoginResponse(generateServiceToken(userName).bind(), null)
            }
        } else {
            either {
                val refreshToken = generateUserToken(userName, TokenMode.REFRESH).bind()
                authRepository.insertToken(userName, refreshToken, isAccess = false).bind()
                LoginResponse(
                    generateUserToken(userName, TokenMode.ACCESS).bind(),
                    refreshToken
                )
            }
        }
    }

    suspend fun logout(user: String) = authRepository.deleteTokensFromUser(user)

    suspend fun refresh(userName: String): Either<ControllerError, LoginResponse?> {
        return either {
            LoginResponse(generateToken(userName, TokenMode.ACCESS, expirationMinutes = 15).bind(), null)
        }

    }

    suspend fun deleteExpiredTokens(): Int {
        return authRepository.deleteExpiredTokens()
    }

    private suspend fun generateToken(
        userName: String,
        tokenMode: TokenMode,
        expirationMinutes: Long? = null
    ): Either<JWTCreationError, String> {
        return try {
            val now = OffsetDateTime.now()
            val jwtBuilder: JWTCreator.Builder = JWT.create()
                .withClaim("username", userName)
                .withClaim("mode", tokenMode.toString())
                .withIssuer(jwtConfig.issuer)
                .withIssuedAt(now.toInstant())

            if (tokenMode == TokenMode.ACCESS) {
                val userActivities: List<Activity> = credentialsService.getUserRoles(userName)
                    .flatMap { rolesService.getRole(it).second }
                jwtBuilder.withClaim("activities", userActivities)
            }

            if (expirationMinutes != null) jwtBuilder.withExpiresAt(now.plusMinutes(expirationMinutes).toInstant())

            val token =
                jwtBuilder.sign(Algorithm.HMAC256(jwtConfig.secret)) // TODO: Consider using a stronger encryption algorithm
            Either.Right(token)

        } catch (e: IllegalArgumentException) {
            Either.Left(JWTCreationError(e.message ?: e.stackTraceToString()))
        } catch (e: JWTCreationException) {
            Either.Left(JWTCreationError(e.message ?: e.stackTraceToString()))
        }
    }

    private suspend fun generateUserToken(userName: String, tokenMode: TokenMode): Either<JWTCreationError, String> {
        return when (tokenMode) {
            TokenMode.ACCESS -> generateToken(userName, tokenMode, expirationMinutes = 15)
            TokenMode.REFRESH -> generateToken(userName, tokenMode, expirationMinutes = 30 * 24 * 60)
        }
    }

    private suspend fun generateServiceToken(userName: String): Either<JWTCreationError, String> {
        return generateToken(userName, TokenMode.ACCESS, expirationMinutes = null)
    }
}