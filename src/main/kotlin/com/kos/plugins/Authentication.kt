package com.kos.plugins

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.kos.activities.Activity
import com.kos.auth.TokenMode
import com.kos.common.JWTConfig
import com.kos.common.toCredentials
import com.kos.credentials.CredentialsService
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.auth.jwt.*
import java.time.OffsetDateTime

data class UserWithActivities(val name: String, val activities: Set<Activity>) : Principal

fun Application.configureAuthentication(credentialsService: CredentialsService, jwtConfig: JWTConfig) {
    install(Authentication) {
        basic("auth-basic") {
            validate { credentials ->
                if (credentialsService.validateCredentials(credentials.toCredentials())) UserIdPrincipal(credentials.name)
                else null
            }
        }

        jwt("auth-jwt") {
            verifier(
                JWT.require(Algorithm.HMAC256(jwtConfig.secret))
                    .withIssuer(jwtConfig.issuer) //TODO: Check issuer is valid
                    .withClaimPresence("username")
                    .withClaimPresence("mode")
                    .withClaimPresence("activities")
                    .build()
            )

            validate { token ->
                //TODO: Would be nice to provide why validation went wrong
                if (TokenMode.fromString(token.payload.getClaim("mode").asString()) != TokenMode.ACCESS) null
                //TODO: Maybe check that expiresAtAsInstant can only be null if user has service role
                else if (token.payload.expiresAtAsInstant != null && token.payload.expiresAtAsInstant.isBefore(
                        OffsetDateTime.now().toInstant()
                    )
                ) null
                else {
                    val username = token.payload.getClaim("username").asString()
                    val activities = token.payload.getClaim("activities").asList(String::class.java).toSet()
                    UserWithActivities(username, activities)
                }
            }
        }

        jwt("auth-jwt-refresh") {
            verifier(
                JWT.require(Algorithm.HMAC256(jwtConfig.secret))
                    .withIssuer(jwtConfig.issuer)
                    .withClaimPresence("username")
                    .withClaimPresence("mode")
                    .build()
            )

            validate { token ->
                //TODO: Would be nice to provide why validation went wrong
                if (TokenMode.fromString(token.payload.getClaim("mode").asString()) != TokenMode.REFRESH) null
                else if (token.payload.expiresAtAsInstant.isBefore(OffsetDateTime.now().toInstant())) null
                else {
                    val username = token.payload.getClaim("username").asString()
                    UserIdPrincipal(username)
                }
            }
        }
    }
}