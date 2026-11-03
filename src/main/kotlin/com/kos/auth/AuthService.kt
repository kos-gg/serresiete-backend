package com.kos.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.JWTCreator
import com.auth0.jwt.algorithms.Algorithm
import com.kos.activities.Activity
import com.kos.auth.repository.AuthRepository
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

    suspend fun login(userName: String): LoginResponse =
        if (credentialsService.getUserRoles(userName).contains(Role.SERVICE)) {
            LoginResponse(generateServiceToken(userName), null)
        } else {
            val refreshToken = generateUserToken(userName, TokenMode.REFRESH)
            authRepository.insertToken(userName, refreshToken, isAccess = false)
            LoginResponse(generateUserToken(userName, TokenMode.ACCESS), refreshToken)
        }

    suspend fun logout(user: String) = authRepository.deleteTokensFromUser(user)

    suspend fun refresh(userName: String): LoginResponse =
        LoginResponse(generateToken(userName, TokenMode.ACCESS, expirationMinutes = 15), null)

    suspend fun deleteExpiredTokens(): Int {
        return authRepository.deleteExpiredTokens()
    }

    private suspend fun generateToken(
        userName: String,
        tokenMode: TokenMode,
        expirationMinutes: Long? = null
    ): String {
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

        return jwtBuilder.sign(Algorithm.HMAC256(jwtConfig.secret)) // TODO: Consider using a stronger encryption algorithm
    }

    private suspend fun generateUserToken(userName: String, tokenMode: TokenMode): String {
        return when (tokenMode) {
            TokenMode.ACCESS -> generateToken(userName, tokenMode, expirationMinutes = 15)
            TokenMode.REFRESH -> generateToken(userName, tokenMode, expirationMinutes = 30 * 24 * 60)
        }
    }

    private suspend fun generateServiceToken(userName: String): String {
        return generateToken(userName, TokenMode.ACCESS, expirationMinutes = null)
    }
}