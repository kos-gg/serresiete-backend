package com.kos.credentials

import com.kos.common.error.respondWithHandledError
import com.kos.plugins.UserWithActivities
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.credentialsRouting(credentialsController: CredentialsController) {
    route("/credentials") {
        authenticate("auth-jwt") {
            post {
                val userWithActivities = call.principal<UserWithActivities>()
                credentialsController.createCredential(
                    userWithActivities?.name,
                    userWithActivities?.activities.orEmpty(),
                    call.receive()
                ).fold({
                    call.respondWithHandledError(it)
                }, {
                    call.respond(HttpStatusCode.Created)
                })
            }
        }
        authenticate("auth-jwt") {
            get {
                val userWithActivities = call.principal<UserWithActivities>()
                credentialsController.getCredentials(
                    userWithActivities?.name,
                    userWithActivities?.activities.orEmpty()
                ).fold({
                    call.respondWithHandledError(it)
                }, {
                    call.respond(HttpStatusCode.OK, it)
                })
            }
        }
        route("/{user}") {
            authenticate("auth-jwt") {
                delete {
                    val userWithActivities = call.principal<UserWithActivities>()
                    credentialsController.deleteCredential(
                        userWithActivities?.name,
                        userWithActivities?.activities.orEmpty(),
                        call.parameters["user"].orEmpty()
                    ).fold({
                        call.respondWithHandledError(it)
                    }, {
                        call.respond(HttpStatusCode.NoContent)
                    })
                }
            }
            authenticate("auth-jwt") {
                get {
                    val userWithActivities = call.principal<UserWithActivities>()
                    credentialsController.getCredential(
                        userWithActivities?.name,
                        userWithActivities?.activities.orEmpty(),
                        call.parameters["user"].orEmpty()
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
                    credentialsController.editCredential(
                        userWithActivities?.name,
                        userWithActivities?.activities.orEmpty(),
                        call.parameters["user"].orEmpty(),
                        call.receive()
                    ).fold({
                        call.respondWithHandledError(it)
                    }, {
                        call.respond(HttpStatusCode.NoContent)
                    })
                }
            }
            authenticate("auth-jwt") {
                patch {
                    val userWithActivities = call.principal<UserWithActivities>()
                    credentialsController.patchCredential(
                        userWithActivities?.name,
                        userWithActivities?.activities.orEmpty(),
                        call.parameters["user"].orEmpty(),
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