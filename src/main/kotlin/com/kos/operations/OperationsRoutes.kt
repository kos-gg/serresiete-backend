package com.kos.operations

import com.kos.common.error.respondWithHandledError
import com.kos.plugins.UserWithActivities
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.operationsRouting(operationsController: OperationsController) {
    route("/operations") {
        authenticate("auth-jwt") {
            get("/{id}") {
                val userWithActivities = call.principal<UserWithActivities>()
                operationsController.getOperationStatus(
                    userWithActivities?.name,
                    call.parameters["id"].orEmpty(),
                    userWithActivities?.activities.orEmpty()
                ).fold({
                    call.respondWithHandledError(it)
                }, {
                    call.respond(OK, it)
                })
            }
        }
    }
}
