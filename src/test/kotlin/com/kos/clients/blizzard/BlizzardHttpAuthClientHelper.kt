package com.kos.clients.blizzard

import com.kos.clients.domain.TokenResponse
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BlizzardHttpAuthClientHelper {

    val client = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {
                    "/token" -> respond(
                        Json.encodeToString(TokenResponse("token", "token", 10, "sub")),
                        HttpStatusCode.OK
                    )

                    else -> error("Unhandled ${request.url.encodedPath}")
                }
            }
        }
    }

    val httpErrorClient = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {
                    "/token" -> respond(
                        content = "invalid credentials",
                        status = HttpStatusCode.Unauthorized
                    )

                    else -> error("Unhandled ${request.url.encodedPath}")
                }
            }
        }
    }

    val jsonErrorClient =
        HttpClient(MockEngine) {
            install(ContentNegotiation) {
                json()
            }
            engine {
                addHandler { request ->
                    when (request.url.encodedPath) {
                        "/token" -> respond(
                            "invalid token",
                            HttpStatusCode.OK
                        )

                        else -> error("Unhandled ${request.url.encodedPath}")
                    }
                }
            }
        }
}