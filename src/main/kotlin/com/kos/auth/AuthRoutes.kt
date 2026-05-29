package com.kos.auth

import com.auth0.jwt.JWT
import com.auth0.jwt.algorithms.Algorithm
import com.auth0.jwt.exceptions.JWTVerificationException
import com.kos.common.JWTConfig
import com.kos.common.error.respondWithHandledError
import com.kos.plugins.UserWithActivities
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import java.time.OffsetDateTime

private const val REFRESH_COOKIE = "refreshToken"
private const val REFRESH_COOKIE_PATH = "/api/auth/refresh"
private const val REFRESH_MAX_AGE = 60L * 60 * 24 * 30
private val IS_HTTPS = System.getenv("ALLOWED_ORIGIN")
    ?.split(",")
    ?.any { it.trim().startsWith("https://") }
    ?: false
private val SAME_SITE = if (IS_HTTPS) mapOf("SameSite" to "None") else emptyMap()

fun Route.authRouting(
    authController: AuthController,
    jwtConfig: JWTConfig
) {

    route("/auth") {
        authenticate("auth-basic") {
            post {
                authController.login(call.principal<UserIdPrincipal>()?.name).fold({
                    call.respondWithHandledError(it)
                }, {
                    if (it.refreshToken != null) {
                        call.response.cookies.append(
                            name = REFRESH_COOKIE,
                            value = it.refreshToken,
                            httpOnly = true,
                            secure = IS_HTTPS,
                            maxAge = REFRESH_MAX_AGE,
                            path = REFRESH_COOKIE_PATH,
                            extensions = SAME_SITE
                        )
                    }
                    call.respond(HttpStatusCode.OK, it)
                })
            }
        }
        authenticate("auth-jwt") {
            delete {
                val userWithActivities = call.principal<UserWithActivities>()
                authController.logout(userWithActivities?.name, userWithActivities?.activities.orEmpty()).fold({
                    call.respondWithHandledError(it)
                }, {
                    call.response.cookies.append(
                        name = REFRESH_COOKIE,
                        value = "",
                        httpOnly = true,
                        secure = IS_HTTPS,
                        maxAge = 0L,
                        path = REFRESH_COOKIE_PATH,
                        extensions = SAME_SITE + mapOf("Max-Age" to "0")
                    )
                    call.respond(HttpStatusCode.OK)
                })
            }
        }
        route("/refresh") {
            post {
                val tokenFromCookie = call.request.cookies[REFRESH_COOKIE]
                val tokenFromHeader = call.request.headers[HttpHeaders.Authorization]
                    ?.removePrefix("Bearer ")
                    ?.trim()
                    ?.takeIf { it.isNotBlank() }
                val token = tokenFromCookie ?: tokenFromHeader

                if (token == null) {
                    call.respond(HttpStatusCode.Unauthorized)
                    return@post
                }

                try {
                    val decoded = JWT.require(Algorithm.HMAC256(jwtConfig.secret))
                        .withIssuer(jwtConfig.issuer)
                        .withClaimPresence("username")
                        .withClaimPresence("mode")
                        .build()
                        .verify(token)

                    if (TokenMode.fromString(decoded.getClaim("mode").asString()) != TokenMode.REFRESH) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@post
                    }
                    if (decoded.expiresAtAsInstant != null && decoded.expiresAtAsInstant.isBefore(OffsetDateTime.now().toInstant())) {
                        call.respond(HttpStatusCode.Unauthorized)
                        return@post
                    }

                    val username = decoded.getClaim("username").asString()
                    authController.refresh(username).fold({
                        call.respondWithHandledError(it)
                    }, {
                        when (it) {
                            null -> call.respond(HttpStatusCode.NotFound)
                            else -> call.respond(HttpStatusCode.OK, it)
                        }
                    })
                } catch (e: Exception) {
                    call.respond(HttpStatusCode.Unauthorized)
                }
            }
        }
    }
}