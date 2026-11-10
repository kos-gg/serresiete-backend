package com.kos.views

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.ensureNotNull
import com.kos.common.error.InvalidQueryParameter
import com.kos.common.error.respondWithHandledError
import com.kos.common.recoverToEither
import com.kos.plugins.UserWithActivities
import io.ktor.http.HttpStatusCode.Companion.OK
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.request.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.viewsRouting(
    viewsController: ViewsController
) {
    authenticate("auth-jwt") {
        route("/views") {
            get {
                val userWithActivities = call.principal<UserWithActivities>()
                either {
                    val gameParameter = "game"
                    val game: Game? =
                        call.request.queryParameters[gameParameter].recoverToEither(
                            {
                                InvalidQueryParameter(
                                    gameParameter,
                                    it,
                                    Game.entries.map { games -> games.toString() })
                            },
                            { Game.fromString(it) }
                        ).bind()

                    val featured: Boolean =
                        call.request.queryParameters["featured"]?.toBoolean() ?: false

                    val page = parsePositiveIntQueryParam("page", call.request.queryParameters["page"]).bind()
                    val limit = parsePositiveIntQueryParam("limit", call.request.queryParameters["limit"]).bind()
                    val includeMetadata = call.request.queryParameters["include"] == "metadata"

                    viewsController.getViews(
                        userWithActivities?.name,
                        userWithActivities?.activities.orEmpty(),
                        GetViewsQuery(game, featured, page, limit, includeMetadata)
                    ).bind()
                }.fold({
                    call.respondWithHandledError(it)
                }, {
                    call.respond(OK, GetViewsResponse(it.first, it.second))
                })
            }
            get("/{id}") {
                val userWithActivities = call.principal<UserWithActivities>()
                viewsController.getView(
                    userWithActivities?.name,
                    call.parameters["id"].orEmpty(),
                    userWithActivities?.activities.orEmpty()
                ).fold({
                    call.respondWithHandledError(it)
                }, {
                    call.respond(OK, it)
                })
            }
            get("/{id}/data") {
                val userWithActivities = call.principal<UserWithActivities>()
                viewsController.getViewData(
                    userWithActivities?.name,
                    call.parameters["id"].orEmpty(),
                    userWithActivities?.activities.orEmpty()
                )
                    .fold({
                        call.respondWithHandledError(it)
                    }, {
                        call.respond(OK, it)
                    })
            }
            get("/{id}/cached-data") {
                val userWithActivities = call.principal<UserWithActivities>()
                viewsController.getViewCachedData(
                    userWithActivities?.name,
                    call.parameters["id"].orEmpty(),
                    userWithActivities?.activities.orEmpty()
                ).fold({
                    call.respondWithHandledError(it)
                }, {
                    call.respond(OK, it)
                })
            }
            post {
                val userWithActivities = call.principal<UserWithActivities>()
                viewsController.createView(
                    userWithActivities?.name,
                    call.receive<ViewRequest>().normalized(),
                    userWithActivities?.activities.orEmpty()
                ).fold({
                    call.respondWithHandledError(it)
                }, {
                    call.respond(OK, it)
                })
            }
            put("/{id}") {
                val userWithActivities = call.principal<UserWithActivities>()
                viewsController.editView(
                    userWithActivities?.name,
                    call.receive<ViewRequest>().normalized(),
                    call.parameters["id"].orEmpty(),
                    userWithActivities?.activities.orEmpty()
                ).fold({
                    call.respondWithHandledError(it)
                }, {
                    call.respond(OK, it)
                })
            }
            patch("/{id}") {
                val userWithActivities = call.principal<UserWithActivities>()
                viewsController.patchView(
                    userWithActivities?.name,
                    call.receive<ViewPatchRequest>().normalized(),
                    call.parameters["id"].orEmpty(),
                    userWithActivities?.activities.orEmpty()
                ).fold({
                    call.respondWithHandledError(it)
                }, {
                    call.respond(OK, it)
                })
            }
            delete("/{id}") {
                val userWithActivities = call.principal<UserWithActivities>()
                viewsController.deleteView(
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

internal fun parsePositiveIntQueryParam(param: String, value: String?): Either<InvalidQueryParameter, Int?> =
    either {
        value?.let { it ->
            ensureNotNull(it.toIntOrNull()?.takeIf { it >= 1 }) {
                InvalidQueryParameter(param, value, listOf("a positive integer"))
            }
        }
    }