package acceptance

import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.http.*

private fun readResource(path: String): String =
    object {}.javaClass.classLoader.getResourceAsStream(path)!!
        .bufferedReader(Charsets.UTF_8).readText()

val mockHttpClient = HttpClient(MockEngine) {
    engine {
        addHandler { request ->
            val json = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
            val path = request.url.encodedPath
            when {
                path.contains("/token") ->
                    respond(readResource("wow-hc/blizzard-token-response.json"), HttpStatusCode.OK, json)
                path.contains("/character-media") ->
                    respond(readResource("wow-hc/blizzard-character-media-response.json"), HttpStatusCode.OK, json)
                path.contains("/equipment") ->
                    respond(readResource("wow-hc/blizzard-equipment-response.json"), HttpStatusCode.OK, json)
                path.contains("/statistics") ->
                    respond(readResource("wow-hc/blizzard-statistics-response.json"), HttpStatusCode.OK, json)
                path.contains("/specializations") ->
                    respond(readResource("wow-hc/blizzard-specializations-response.json"), HttpStatusCode.OK, json)
                path.contains("/data/wow/guild/") ->
                    respond(readResource("wow-hc/blizzard-guild-roster-response.json"), HttpStatusCode.OK, json)
                path.contains("/data/wow/realm/") ->
                    respond(readResource("wow-hc/blizzard-realm-response.json"), HttpStatusCode.OK, json)
                path.contains("/profile/wow/character/") -> {
                    val status = MockConfig.blizzardProfileStatusOverride ?: HttpStatusCode.OK
                    if (status == HttpStatusCode.OK)
                        respond(readResource("wow-hc/blizzard-character-profile-response.json"), status, json)
                    else
                        respond("", status, json)
                }
                path.contains("/mythic-plus/static-data") ->
                    respond(readResource("wow/raiderio-expansion-seasons-response.json"), HttpStatusCode.OK, json)
                path.contains("/characters/profile") ->
                    respond(readResource("wow/raiderio-profile-response.json"), HttpStatusCode.OK, json)
                path.contains("/season-cutoffs") ->
                    respond(readResource("wow/raiderio-cutoff-response.json"), HttpStatusCode.OK, json)
                path.contains("/run-details") ->
                    respond(readResource("wow/raiderio-run-details-response.json"), HttpStatusCode.OK, json)
                path.contains("/accounts/by-riot-id") ->
                    respond(readResource("riot-get-puuid-by-riot-id-response.json"), HttpStatusCode.OK, json)
                path.contains("/accounts/by-puuid") ->
                    respond(readResource("riot-get-account-by-puuid-response.json"), HttpStatusCode.OK, json)
                path.contains("/summoners/by-puuid") ->
                    respond(readResource("riot-get-summoner-by-puuid-response.json"), HttpStatusCode.OK, json)
                path.contains("/entries/by-puuid") ->
                    respond(readResource("riot-get-leagues-by-summoner-id.json"), HttpStatusCode.OK, json)
                path.contains("/matches/by-puuid") ->
                    respond(readResource("riot-get-matches-by-puuid-response.json"), HttpStatusCode.OK, json)
                path.contains("/matches/") ->
                    respond(readResource("riot-get-match-by-id-response.json"), HttpStatusCode.OK, json)
                else -> respond("{}", HttpStatusCode.OK, json)
            }
        }
    }
}
