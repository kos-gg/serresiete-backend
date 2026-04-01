package com.kos.clients.raiderio

import com.kos.clients.domain.*
import java.time.OffsetDateTime
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*

object RaiderIoHttpClientHelper {

    object ResourceLoader {
        fun readResource(path: String): String =
            object {}.javaClass.classLoader
                .getResourceAsStream(path)!!
                .bufferedReader(Charsets.UTF_8)
                .use { it.readText() }
    }

    val client = HttpClient(MockEngine) {
        install(ContentNegotiation) {
            json()
        }
        engine {
            addHandler { request ->
                when (request.url.encodedPath) {

                    "/api/v1/mythic-plus/static-data" -> {
                        val response = ResourceLoader.readResource("wow/raiderio-tww-seasons.json")
                        respond(
                            content = response,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/api/v1/mythic-plus/season-cutoffs" -> {
                        val response = ResourceLoader.readResource("wow/raiderio-cutoff-response.json")
                        respond(
                            content = response,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/api/v1/characters/profile" -> {
                        val response = when (request.url.parameters["fields"]) {
                            "talents" -> ResourceLoader.readResource("wow/raiderio-classic-talents-response.json")
                            else -> ResourceLoader.readResource("wow/raiderio-profile-response.json")
                        }
                        respond(
                            content = response,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/api/v1/mythic-plus/run-details" -> {
                        val response = ResourceLoader.readResource("wow/raiderio-run-details-response.json")
                        respond(
                            content = response,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    else -> error("Unhandled ${request.url.encodedPath}")
                }
            }
        }
    }

    val raiderioProfileResponse =
        RaiderIoResponse(
            RaiderIoProfile(
                name = "Nareez",
                realm = "Blackrock",
                region = "eu",
                `class` = "Warlock",
                spec = "Affliction",
                seasonScores = listOf(
                    MythicPlusSeasonScore(
                        "season-df-3",
                        SeasonScores(2708.4, 2708.4, 0.0, 0.0, 0.0)
                    )
                ),
                mythicPlusRanks = MythicPlusRanks(
                    overall = MythicPlusRank(43389, 24021, 887),
                    `class` = MythicPlusRank(1989, 1077, 58),
                    specs = mapOf(
                        "spec_265" to MythicPlusRank(4, 2, 2),
                        "spec_266" to MythicPlusRank(0, 0, 0),
                        "spec_267" to MythicPlusRank(0, 0, 0)
                    )
                ),
                mythicPlusBestRuns = listOf(
                    MythicPlusRun(
                        4462779L,
                        "Throne of the Tides",
                        "TOTT",
                        20,
                        2,
                        OffsetDateTime.parse("2023-11-25T12:12:22.000Z"),
                        1380287L,
                        174.0F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4462779-20-throne-of-the-tides",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        3814291L,
                        "Atal'Dazar",
                        "AD",
                        20,
                        2,
                        OffsetDateTime.parse("2023-11-23T20:35:15.000Z"),
                        1260658L,
                        173.8F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3814291-20-ataldazar",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        4879212L,
                        "Waycrest Manor",
                        "WM",
                        20,
                        2,
                        OffsetDateTime.parse("2023-11-26T13:06:05.000Z"),
                        1605776L,
                        173.5F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4879212-20-waycrest-manor",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        3709222L,
                        "Darkheart Thicket",
                        "DHT",
                        20,
                        2,
                        OffsetDateTime.parse("2023-11-23T16:32:21.000Z"),
                        1310595L,
                        173.4F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3709222-20-darkheart-thicket",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        3628977L,
                        "Black Rook Hold",
                        "BRH",
                        20,
                        2,
                        OffsetDateTime.parse("2023-11-23T10:33:37.000Z"),
                        1724166L,
                        172.5F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3628977-20-black-rook-hold",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        3775347L,
                        "DOTI: Galakrond's Fall",
                        "FALL",
                        20,
                        1,
                        OffsetDateTime.parse("2023-11-23T20:04:33.000Z"),
                        1642682L,
                        172.4F,
                        "https://raider.io/mythic-plus-runs/season-df-3/3775347-20-doti-galakronds-fall",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        4267361L,
                        "The Everbloom",
                        "EB",
                        20,
                        1,
                        OffsetDateTime.parse("2023-11-24T19:42:55.000Z"),
                        1719391L,
                        171.7F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4267361-20-everbloom",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    ),
                    MythicPlusRun(
                        4382848L,
                        "DOTI: Murozond's Rise",
                        "RISE",
                        20,
                        1,
                        OffsetDateTime.parse("2023-11-25T09:38:33.000Z"),
                        2097610L,
                        170.0F,
                        "https://raider.io/mythic-plus-runs/season-df-3/4382848-20-doti-murozonds-rise",
                        listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
                    )
                )

            ),
            specs = listOf(
                MythicPlusRankWithSpecName("Affliction", 2708.4, 4, 2, 2),
                MythicPlusRankWithSpecName("Demonology", 0.0, 0, 0, 0),
                MythicPlusRankWithSpecName("Destruction", 0.0, 0, 0, 0)
            )
        )

    val mythicPlusRunJson = """
        {
            "keystone_run_id": 4462779,
            "dungeon": "Throne of the Tides",
            "short_name": "TOTT",
            "mythic_level": 20,
            "num_keystone_upgrades": 2,
            "completed_at": "2023-11-25T12:12:22.000Z",
            "clear_time_ms": 1380287,
            "score": 174.0,
            "url": "https://raider.io/mythic-plus-runs/season-df-3/4462779-20-throne-of-the-tides",
            "affixes": [
                { "name": "Tyrannical" },
                { "name": "Entangling" },
                { "name": "Bursting" }
            ]
        }
    """.trimIndent()

    val runDetails = RunDetails(
        roster = listOf(
            RunDetailsRosterEntry(RunDetailsCharacter("Nareez", RunDetailsCharacterClass("Warlock"), RunDetailsCharacterSpec("Affliction"), RunDetailsCharacterRealm("Blackrock"))),
            RunDetailsRosterEntry(RunDetailsCharacter("Surmana", RunDetailsCharacterClass("Warrior"), RunDetailsCharacterSpec("Protection"), RunDetailsCharacterRealm("Soulseeker")))
        ),
        loggedDetails = LoggedDetails(
            deaths = listOf(
                RunDetailsDeath(291586163L, 1441041, 3268098),
                RunDetailsDeath(301703640L, 1446140, 3268098),
                RunDetailsDeath(113975488L, 914295, null)
            )
        )
    )

    val mythicPlusRun = MythicPlusRun(
        runId = 4462779L,
        dungeon = "Throne of the Tides",
        shortName = "TOTT",
        keyLevel = 20,
        upgrades = 2,
        completionTme = OffsetDateTime.parse("2023-11-25T12:12:22.000Z"),
        clearTimeMs = 1380287L,
        score = 174.0F,
        url = "https://raider.io/mythic-plus-runs/season-df-3/4462779-20-throne-of-the-tides",
        affixes = listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
    )
}