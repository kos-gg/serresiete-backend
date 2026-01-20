package com.kos.activities

import com.kos.common.error.respondWithHandledError
import com.kos.plugins.UserWithActivities
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.activitiesRouting(
    activitiesController: ActivitiesController
) {
    route("/activities") {
        authenticate("auth-jwt") {
            get {
                val userWithActivities = call.principal<UserWithActivities>()
                activitiesController.getActivities(userWithActivities?.name, userWithActivities?.activities.orEmpty())
                    .fold({
                        call.respondWithHandledError(it)
                    }, {
                        call.respond(HttpStatusCode.OK, it)
                    })
            }
        }
    }
}