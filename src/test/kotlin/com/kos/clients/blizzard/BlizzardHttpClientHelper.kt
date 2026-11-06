package com.kos.clients.blizzard

import com.kos.datacache.BlizzardMockHelper.getWowCharacterMediaResponse
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponseString
import com.kos.datacache.BlizzardMockHelper.getWowEquipmentResponseString
import com.kos.datacache.BlizzardMockHelper.getWowGuildRosterResponse
import com.kos.datacache.BlizzardMockHelper.getWowItemMediaResponse
import com.kos.datacache.BlizzardMockHelper.getWowItemResponseString
import com.kos.datacache.BlizzardMockHelper.getWowRetailGuildRosterResponse
import com.kos.datacache.BlizzardMockHelper.getWowSpecializationsResponseString
import com.kos.datacache.BlizzardMockHelper.getWowStatsResponse
import com.kos.datacache.BlizzardMockHelper.notHardcoreRealm
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

object BlizzardHttpClientHelper {

    val json = Json {
        ignoreUnknownKeys = true
    }

    private val mythicKeystoneProfileResponse: String =
        object {}.javaClass.classLoader
            .getResourceAsStream("unit/wow/blizzard-mythic-keystone-profile-response.json")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private val foreverCharacterResponse: String =
        object {}.javaClass.classLoader
            .getResourceAsStream("unit/wow/blizzard-forever-character-response.json")!!
            .bufferedReader(Charsets.UTF_8)
            .use { it.readText() }

    private const val NOT_FOUND = """{"code":404,"type":"BLZWEBAPI00000404","detail":"Not Found"}"""

    val client = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {

                    "/profile/wow/character/siempre-tarde" ->
                        if (request.headers["Battlenet-Namespace"] == "profile-forever-eu")
                            respond(foreverCharacterResponse, HttpStatusCode.OK)
                        else respond(NOT_FOUND, HttpStatusCode.NotFound)

                    "/profile/wow/character/siempre-tarde/character-media" ->
                        if (request.headers["Battlenet-Namespace"] == "profile-forever-eu")
                            respond(json.encodeToString(getWowCharacterMediaResponse), HttpStatusCode.OK)
                        else respond(NOT_FOUND, HttpStatusCode.NotFound)

                    "/profile/wow/character/unknown-tarde" ->
                        respond(NOT_FOUND, HttpStatusCode.NotFound)

                    "/profile/wow/character/realm/name" ->
                        respond(getWowCharacterResponseString, HttpStatusCode.OK)

                    "/profile/wow/character/realm/name/character-media" ->
                        respond(
                            json.encodeToString(getWowCharacterMediaResponse),
                            HttpStatusCode.OK
                        )

                    "/profile/wow/character/realm/name/equipment" ->
                        respond(getWowEquipmentResponseString, HttpStatusCode.OK)

                    "/profile/wow/character/realm/name/specializations" ->
                        respond(getWowSpecializationsResponseString, HttpStatusCode.OK)

                    "/profile/wow/character/realm/name/statistics" ->
                        respond(json.encodeToString(getWowStatsResponse), HttpStatusCode.OK)

                    "/data/wow/media/item/123" ->
                        respond(json.encodeToString(getWowItemMediaResponse), HttpStatusCode.OK)

                    "/data/wow/item/18421" ->
                        respond(getWowItemResponseString, HttpStatusCode.OK)

                    "/data/wow/realm/1" ->
                        respond(json.encodeToString(notHardcoreRealm), HttpStatusCode.OK)

                    "/data/wow/guild/realm/guild/roster" ->
                        when (request.url.parameters["namespace"]) {
                            "profile-us" ->
                                respond(json.encodeToString(getWowRetailGuildRosterResponse), HttpStatusCode.OK)

                            else ->
                                respond(json.encodeToString(getWowGuildRosterResponse), HttpStatusCode.OK)
                        }

                    "/profile/wow/character/realm/name/mythic-keystone-profile/season/16" ->
                        respond(mythicKeystoneProfileResponse, HttpStatusCode.OK)

                    "/profile/wow/character/realm/unknownspec/mythic-keystone-profile/season/16" ->
                        respond(
                            mythicKeystoneProfileResponse.replace("\"id\": 66", "\"id\": 9999"),
                            HttpStatusCode.OK
                        )

                    "/profile/wow/character/realm/unrated/mythic-keystone-profile/season/16" ->
                        respond("""{"season":{"id":16}}""", HttpStatusCode.OK)

                    "/profile/wow/character/realm/unknown",
                    "/data/wow/guild/realm/unknown/roster" ->
                        respond("""{"code":404,"type":"BLZWEBAPI00000404","detail":"Not Found"}""", HttpStatusCode.NotFound)

                    "/profile/wow/character/realm/unknown/mythic-keystone-profile/season/16" ->
                        respond("""{"code":404,"type":"BLZWEBAPI00000404","detail":"Not Found"}""", HttpStatusCode.NotFound)

                    else ->
                        error("Unhandled ${request.url.encodedPath}")
                }
            }
        }
    }
}
