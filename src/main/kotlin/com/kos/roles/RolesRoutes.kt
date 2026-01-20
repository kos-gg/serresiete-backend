package com.kos.roles

import com.kos.common.error.respondWithHandledError
import com.kos.plugins.UserWithActivities
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.rolesRouting(
    rolesController: RolesController
) {
    route("/roles") {
        authenticate("auth-jwt") {
            get {
                val userWithActivities = call.principal<UserWithActivities>()
                rolesController.getRoles(userWithActivities?.name, userWithActivities?.activities.orEmpty()).fold({
                    call.respondWithHandledError(it)
                }, {
                    call.respond(HttpStatusCode.OK, it)
                })
            }
        }
        route("/{role}") {
            authenticate("auth-jwt") {
                get {
                    val userWithActivities = call.principal<UserWithActivities>()
                    rolesController.getRole(
                        userWithActivities?.name,
                        userWithActivities?.activities.orEmpty(),
                        Role.fromString(call.parameters["role"].orEmpty())
                    ).fold({
                        call.respondWithHandledError(it)
                    }, {
                        call.respond(HttpStatusCode.OK, it)
                    })
                }
            }
            authenticate("auth-jwt") {
                put {
                    val userWithActivities = call.principal<UserWithActivities>()
                    rolesController.setActivities(
                        userWithActivities?.name,
                        userWithActivities?.activities.orEmpty(),
                        Role.fromString(call.parameters["role"].orEmpty()),
                        call.receive()
                    ).fold({
                        call.respondWithHandledError(it)
                    }, {
                        call.respond(HttpStatusCode.NoContent)
                    })
                }
            }
        }
    }
}