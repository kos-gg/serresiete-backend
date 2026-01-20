package com.kos.auth

import com.kos.common.error.respondWithHandledError
import com.kos.plugins.UserWithActivities
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.authRouting(
    authController: AuthController
) {

    route("/auth") {
        authenticate("auth-basic") {
            post {
                authController.login(call.principal<UserIdPrincipal>()?.name).fold({
                    call.respondWithHandledError(it)
                }, {
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
                    call.respond(HttpStatusCode.OK)
                })
            }
        }
        route("/refresh") {
            authenticate("auth-jwt-refresh") {
                post {
                    authController.refresh(call.principal<UserIdPrincipal>()?.name).fold({
                        call.respondWithHandledError(it)
                    }, {
                        when (it) {
                            null -> call.respond(HttpStatusCode.NotFound)
                            else -> call.respond(HttpStatusCode.OK, it)
                        }
                    })
                }
            }
        }
    }
}