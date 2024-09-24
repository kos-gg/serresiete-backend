package com.kos.raiderio

import arrow.core.Either
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.nio.charset.StandardCharsets

object RaiderioHttpClientHelper {

    val raiderioProfileResponse =
        RaiderIoResponse(
            RaiderIoProfile(
                "Nareez",
                "Warlock",
                "Affliction",
                listOf(MythicPlusSeasonScore("season-df-3", SeasonScores(2708.4, 2708.4, 0.0, 0.0, 0.0))),
                MythicPlusRanks(MythicPlusRank(43389, 24021, 887), MythicPlusRank(1989, 1077, 58)),
                listOf(
                    MythicPlusRun(
                        "Throne of the Tides",
                        "TOTT",
                        20,
                        2,
                        174.0F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4462779-20-throne-of-the-tides",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting")),
                    ),
                    MythicPlusRun(
                        "Atal'Dazar",
                        "AD",
                        20,
                        2,
                        173.8F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3814291-20-ataldazar",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        "Waycrest Manor",
                        "WM",
                        20,
                        2,
                        173.5F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4879212-20-waycrest-manor",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        "Darkheart Thicket",
                        "DHT",
                        20,
                        2,
                        173.4F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3709222-20-darkheart-thicket",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        "Black Rook Hold",
                        "BRH",
                        20,
                        2,
                        172.5F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3628977-20-black-rook-hold",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        "DOTI: Galakrond's Fall",
                        "FALL",
                        20,
                        1,
                        172.4F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3775347-20-doti-galakronds-fall",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        "The Everbloom",
                        "EB",
                        20,
                        1,
                        171.7F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4267361-20-everbloom",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        "DOTI: Murozond's Rise",
                        "RISE",
                        20,
                        1,
                        170.0F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4382848-20-doti-murozonds-rise",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    )
                )
            ), listOf(
                MythicPlusRankWithSpecName("Affliction", 2708.4, 4, 2, 2),
                MythicPlusRankWithSpecName("Demonology", 0.0, 0, 0, 0),
                MythicPlusRankWithSpecName("Destruction", 0.0, 0, 0, 0)
            )
        )

    val client = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {
                    "/mythic-plus/season-cutoffs" -> respond("content", HttpStatusCode.OK)
                    "/api/v1/characters/profile" -> {
                        val response = javaClass.classLoader
                            .getResourceAsStream("raiderio-profile-response.json")!!
                            .bufferedReader(StandardCharsets.UTF_8)
                            .use { it.readText() }
                        respond(response, HttpStatusCode.OK)
                    }

                    else -> error("Unhandled ${request.url.encodedPath}")
                }
            }
        }
    }
}