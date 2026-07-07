package com.kos.auth

import com.kos.common.error.respondWithHandledError
import com.kos.plugins.UserWithActivities
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*
import io.ktor.util.pipeline.*

private const val REFRESH_COOKIE = "refreshToken"
private const val REFRESH_COOKIE_PATH = "/api/auth/refresh"
private const val REFRESH_MAX_AGE = 60L * 60 * 24 * 30

fun Route.authRouting(
    authController: AuthController,
    authConfig: AuthConfig
) {
    route("/auth") {
        authenticate("auth-basic") {
            post {
                authController.login(call.principal<UserIdPrincipal>()?.name)
                    .fold({
                        call.respondWithHandledError(it)
                    }, {
                        if (it.refreshToken != null) {
                            appendCookie(it.refreshToken, authConfig)
                        }
                        call.respond(HttpStatusCode.OK, it)
                    })
            }
        }
        authenticate("auth-jwt") {
            delete {
                val userWithActivities = call.principal<UserWithActivities>()
                authController.logout(userWithActivities?.name, userWithActivities?.activities.orEmpty())
                    .fold({
                        call.respondWithHandledError(it)
                    }, {
                        clearCookie(authConfig)
                        call.respond(HttpStatusCode.OK)
                    })
            }
        }
        authenticate("auth-jwt-refresh") {
            post("/refresh") {
                val username = call.principal<UserIdPrincipal>()?.name
                    ?: return@post call.respond(HttpStatusCode.Unauthorized)

                authController.refresh(username)
                    .fold(
                        { call.respondWithHandledError(it) },
                        {
                            when (it) {
                                null -> call.respond(HttpStatusCode.NotFound)
                                else -> call.respond(HttpStatusCode.OK, it)
                            }
                        }
                    )
            }
        }
    }
}

private fun PipelineContext<Unit, ApplicationCall>.clearCookie(authConfig: AuthConfig) {
    call.response.cookies.append(
        name = REFRESH_COOKIE,
        value = "",
        httpOnly = true,
        secure = authConfig.isHttps,
        maxAge = 0L,
        path = REFRESH_COOKIE_PATH,
        extensions = authConfig.sameSite + mapOf("Max-Age" to "0")
    )
}

private fun PipelineContext<Unit, ApplicationCall>.appendCookie(
    refreshToken: String,
    authConfig: AuthConfig
) {
    call.response.cookies.append(
        name = REFRESH_COOKIE,
        value = refreshToken,
        httpOnly = true,
        secure = authConfig.isHttps,
        maxAge = REFRESH_MAX_AGE,
        path = REFRESH_COOKIE_PATH,
        extensions = authConfig.sameSite
    )
}

