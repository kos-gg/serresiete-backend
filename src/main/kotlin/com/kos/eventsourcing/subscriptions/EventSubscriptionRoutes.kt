package com.kos.eventsourcing.subscriptions

import com.kos.common.respondWithHandledError
import com.kos.plugins.UserWithActivities
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.subscriptionsRouting(eventSubscriptionController: EventSubscriptionController) {
    route("subscriptions") {
        authenticate("auth-jwt") {
            get {
                val userWithActivities = call.principal<UserWithActivities>()

                eventSubscriptionController.getEventSubscriptions(
                    userWithActivities?.name,
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