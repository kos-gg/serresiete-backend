package com.kos.entities

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
        route("/search") {
            authenticate("auth-jwt") {
                get {
                    fun parametersToEntityRequest(parameters: Parameters): Pair<CreateEntityRequest, Game>? {
                        val name = parameters["name"]
                        return when (parameters["game"]) {
                            Game.WOW_HC.toString(), Game.WOW.toString() -> {
                                val region = parameters["region"]
                                val realm = parameters["realm"]
                                if (name == null || realm == null || region == null) null
                                else Pair(WowEntityRequest(name, region, realm), Game.WOW_HC)
                            }

                            Game.WOW.toString() -> {
                                val region = parameters["region"]
                                val realm = parameters["realm"]
                                if (name == null || realm == null || region == null) null
                                else Pair(WowEntityRequest(name, region, realm), Game.WOW)
                            }

                            Game.LOL.toString() -> {
                                val tag = parameters["server"]
                                if (name == null || tag == null) null
                                else Pair(LolEntityRequest(name, tag), Game.LOL)
                            }

                            else -> null
                        }
                    }

                    val userWithActivities = call.principal<UserWithActivities>()

                    entitiesController.getEntityData(
                        parametersToEntityRequest(call.request.queryParameters),
                        userWithActivities?.activities.orEmpty()
                    ).fold({
                        call.respondWithHandledError(it)
                    }, {
                        call.respond(HttpStatusCode.OK, it)
                    })
                }
            }
        }
    }
}