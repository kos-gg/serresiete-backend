package com.kos.entities

import arrow.core.Either
import arrow.core.raise.either
import com.kos.common.BadRequest
import com.kos.common.respondWithHandledError
import com.kos.plugins.UserWithActivities
import com.kos.views.Game
import io.ktor.http.*
import io.ktor.server.application.*
import io.ktor.server.auth.*
import io.ktor.server.response.*
import io.ktor.server.routing.*

fun Route.entitiesRouting(
    entitiesController: EntitiesController
) {
    route("/entities") {
        authenticate("auth-jwt") {
            get {
                fun parametersToEntityRequest(parameters: Parameters): Either<BadRequest, Pair<CreateEntityRequest, Game>> {
                    return either {
                        val name = parameters["name"]
                        when (val game = parameters["game"]) {
                            Game.WOW_HC.toString() -> {
                                val region = parameters["region"]
                                val realm = parameters["realm"]
                                if (name == null || realm == null || region == null)
                                    raise(BadRequest("Incorrect search parameters for wow hardcore entity: name: $name, realm: $realm, region: $region"))
                                else Pair(WowEntityRequest(name, region, realm), Game.WOW_HC)
                            }

                            Game.WOW.toString() -> {
                                val region = parameters["region"]
                                val realm = parameters["realm"]
                                if (name == null || realm == null || region == null)
                                    raise(BadRequest("Incorrect search parameters for wow entity: name: $name, realm: $realm, region: $region"))
                                else Pair(WowEntityRequest(name, region, realm), Game.WOW)
                            }

                            Game.LOL.toString() -> {
                                val tag = parameters["tag"]
                                if (name == null || tag == null)
                                    raise(BadRequest("Incorrect search parameters for lol entity: name: $name, tag: $tag"))
                                else Pair(LolEntityRequest(name, tag), Game.LOL)
                            }

                            else -> raise(BadRequest("Unknown Game: $game"))
                        }
                    }
                }

                parametersToEntityRequest(call.request.queryParameters).fold(
                    ifLeft = { call.respondWithHandledError(it) },
                    ifRight = {
                        val userWithActivities = call.principal<UserWithActivities>()
                        entitiesController.getEntityData(
                            userWithActivities?.name,
                            userWithActivities?.activities.orEmpty(),
                            it
                        ).fold({
                            call.respondWithHandledError(it)
                        }, {
                            call.respond(HttpStatusCode.OK, it)
                        })
                    }
                )


            }
        }
    }
}