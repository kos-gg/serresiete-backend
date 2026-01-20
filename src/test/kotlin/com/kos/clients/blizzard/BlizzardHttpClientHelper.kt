package com.kos.clients.blizzard

import com.kos.datacache.BlizzardMockHelper.getWowCharacterMediaResponse
import com.kos.datacache.BlizzardMockHelper.getWowCharacterResponseString
import com.kos.datacache.BlizzardMockHelper.getWowEquipmentResponseString
import com.kos.datacache.BlizzardMockHelper.getWowGuildRosterResponse
import com.kos.datacache.BlizzardMockHelper.getWowItemMediaResponse
import com.kos.datacache.BlizzardMockHelper.getWowItemResponseString
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

    val client = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {

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
                        respond(json.encodeToString(getWowGuildRosterResponse), HttpStatusCode.OK)

                    else ->
                        error("Unhandled ${request.url.encodedPath}")
                }
            }
        }
    }
}
