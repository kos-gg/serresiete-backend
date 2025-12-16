package com.kos.tasks

import arrow.core.raise.either
import com.kos.common.error.InvalidQueryParameter
import com.kos.common.recoverToEither
import com.kos.common.error.respondWithHandledError
import com.kos.plugins.UserWithActivities
import io.ktor.http.*
import io.ktor.http.HttpHeaders.Location
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.tasksRouting(tasksController: TasksController) {

    route("/tasks") {
        authenticate("auth-jwt") {
            post {
                val userWithActivities = call.principal<UserWithActivities>()
                tasksController.runTask(
                    userWithActivities?.name,
                    call.receive(),
                    userWithActivities?.activities.orEmpty()
                ).fold({
                    call.respondWithHandledError(it)
                }, {
                    call.response.headers.append(Location, "/tasks/$it")
                    call.respond(HttpStatusCode.Created)
                })
            }
        }
        authenticate("auth-jwt") {
            get {
                val userWithActivities = call.principal<UserWithActivities>()
                either {
                    val taskTypeParameter = "type"
                    val taskType: TaskType? =
                        call.request.queryParameters[taskTypeParameter].recoverToEither(
                            { InvalidQueryParameter(taskTypeParameter, it, TaskType.entries.map { taskTypes -> taskTypes.toString() }) },
                            { TaskType.fromString(it) }
                        ).bind()

                    tasksController.getTasks(userWithActivities?.name, userWithActivities?.activities.orEmpty(), taskType).bind()
                }.fold({
                    call.respondWithHandledError(it)
                }, {
                    call.respond(OK, it)
                })
            }
        }
        route("/{id}") {
            authenticate("auth-jwt") {
                get {
                    val userWithActivities = call.principal<UserWithActivities>()
                    tasksController.getTask(
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
}