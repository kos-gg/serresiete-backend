package com.kos.clients.riot

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.nio.charset.StandardCharsets

object RiotHTTPClientHelper {
    private fun responseFromResource(resource: String) = javaClass.classLoader
        .getResourceAsStream(resource)!!
        .bufferedReader(StandardCharsets.UTF_8)
        .use { it.readText() }

    val client = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {
                    "/riot/account/v1/accounts/by-riot-id/GTP+ZeroMVPs/WOW" -> respond(
                        responseFromResource("riot-get-puuid-by-riot-id-response.json"),
                        HttpStatusCode.OK
                    )

                    "/lol/summoner/v4/summoners/by-puuid/vJre0esG5sIx3rvCAe-YVsDfqCIMV5b2P-61wrYZ4w-hs9u_Ek8dVlo-KLo-GNA4NumLV1YTNxeCmA" ->
                        respond(
                            responseFromResource("riot-get-summoner-by-puuid-response.json"),
                            HttpStatusCode.OK
                        )

                    "/lol/match/v5/matches/by-puuid/vJre0esG5sIx3rvCAe-YVsDfqCIMV5b2P-61wrYZ4w-hs9u_Ek8dVlo-KLo-GNA4NumLV1YTNxeCmA/ids" ->
                        respond(
                            responseFromResource("riot-get-matches-by-puuid-response.json"),
                            HttpStatusCode.OK
                        )


                    "/lol/match/v5/matches/EUW1_7130322326" ->
                        respond(
                            responseFromResource("riot-get-match-by-id-response.json"),
                            HttpStatusCode.OK
                        )


                    "/lol/league/v4/entries/by-puuid/XpUAakpMee4budbZ_KVchTTxwkN4OHgqjbYa0r4pXR_Ya6E" ->
                        respond(
                            responseFromResource("riot-get-leagues-by-summoner-id.json"),
                            HttpStatusCode.OK
                        )

                    "/riot/account/v1/accounts/by-puuid/XpUAakpMee4budbZ_KVchTTxwkN4OHgqjbYa0r4pXR_Ya6E" ->
                        respond(
                            responseFromResource("riot-get-account-by-puuid-response.json"),
                            HttpStatusCode.OK
                        )
                    else -> error("Unhandled ${request.url.encodedPath}")
                }
            }
        }
    }
}