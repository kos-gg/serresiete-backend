package com.kos.clients.raiderio

import com.kos.datacache.domain.*
import io.ktor.client.*
import io.ktor.client.engine.mock.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import java.time.OffsetDateTime
import com.kos.sources.wow.WowCharacterProfile

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
                        val response = ResourceLoader.readResource("unit/wow/raiderio-tww-seasons.json")
                        respond(
                            content = response,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/api/v1/mythic-plus/season-cutoffs" -> {
                        val response = ResourceLoader.readResource("unit/wow/raiderio-cutoff-response.json")
                        respond(
                            content = response,
                            status = HttpStatusCode.OK,
                            headers = headersOf(HttpHeaders.ContentType, "application/json")
                        )
                    }

                    "/api/v1/characters/profile" -> {
                        val response = when (request.url.parameters["fields"]) {
                            "talents" -> ResourceLoader.readResource("unit/wow/raiderio-classic-talents-response.json")
                            else -> ResourceLoader.readResource("unit/wow/raiderio-profile-response.json")
                        }
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

    val raiderioProfile =
        WowCharacterProfile(
            name = "Apodruid",
            realm = "Zul'jin",
            region = "eu",
            race = "Night Elf",
            faction = "alliance",
            `class` = "Druid",
            spec = "Guardian",
            score = 4131.0,
            overallRank = MythicPlusRank(2531, 691, 6),
            classRank = MythicPlusRank(162, 97, 1),
            specRanks = listOf(
                MythicPlusRankWithSpecName("Balance", 3325.0, 5517, 2496, 66),
                MythicPlusRankWithSpecName("Feral", 0.0, 0, 0, 0),
                MythicPlusRankWithSpecName("Guardian", 4131.0, 139, 87, 1),
                MythicPlusRankWithSpecName("Restoration Druid", 0.0, 0, 0, 0)
            ),
            recentRuns = listOf(
                MythicPlusRun(
                    24342898L,
                    586,
                    "Den of Nalorakk",
                    "DON",
                    22,
                    1,
                    OffsetDateTime.parse("2026-11-02T10:03:59.000Z"),
                    1752194L,
                    1920999L,
                    518.3F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/24342898-22-den-of-nalorakk",
                    listOf(Affix("Fortified"), Affix("Tyrannical"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    24002306L,
                    584,
                    "The Blinding Vale",
                    "BV",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-31T14:28:49.000Z"),
                    1748598L,
                    1800999L,
                    516.1F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/24002306-22-the-blinding-vale",
                    listOf(Affix("Fortified"), Affix("Tyrannical"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23993428L,
                    399,
                    "Ruby Life Pools",
                    "RLP",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-31T13:40:59.000Z"),
                    1651151L,
                    1680999L,
                    515.6F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23993428-22-ruby-life-pools",
                    listOf(Affix("Fortified"), Affix("Tyrannical"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23980171L,
                    250,
                    "Temple of Sethraliss",
                    "TOS",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-31T11:58:51.000Z"),
                    1830289L,
                    1920999L,
                    516.8F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23980171-22-temple-of-sethraliss",
                    listOf(Affix("Fortified"), Affix("Tyrannical"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23845768L,
                    399,
                    "Ruby Life Pools",
                    "RLP",
                    22,
                    0,
                    OffsetDateTime.parse("2026-10-30T16:40:50.000Z"),
                    1690164L,
                    1680999L,
                    319.8F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23845768-22-ruby-life-pools",
                    listOf(Affix("Fortified"), Affix("Tyrannical"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23798784L,
                    586,
                    "Den of Nalorakk",
                    "DON",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-30T11:57:20.000Z"),
                    1783513L,
                    1920999L,
                    517.7F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23798784-22-den-of-nalorakk",
                    listOf(Affix("Fortified"), Affix("Tyrannical"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23793908L,
                    249,
                    "Kings' Rest",
                    "KR",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-30T10:59:58.000Z"),
                    1952439L,
                    1980999L,
                    515.5F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23793908-22-kings-rest",
                    listOf(Affix("Fortified"), Affix("Tyrannical"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23486318L,
                    249,
                    "Kings' Rest",
                    "KR",
                    22,
                    0,
                    OffsetDateTime.parse("2026-10-28T10:16:08.000Z"),
                    2002059L,
                    1980999L,
                    319.6F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23486318-22-kings-rest",
                    listOf(Affix("Fortified"), Affix("Tyrannical"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23418509L,
                    399,
                    "Ruby Life Pools",
                    "RLP",
                    22,
                    0,
                    OffsetDateTime.parse("2026-10-27T21:39:56.000Z"),
                    1705774L,
                    1680999L,
                    319.4F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23418509-22-ruby-life-pools",
                    listOf(Affix("Tyrannical"), Affix("Fortified"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23354844L,
                    249,
                    "Kings' Rest",
                    "KR",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-27T15:37:33.000Z"),
                    1975011L,
                    1980999L,
                    515.1F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23354844-22-kings-rest",
                    listOf(Affix("Tyrannical"), Affix("Fortified"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                )
            ),
            bestRuns = listOf(
                MythicPlusRun(
                    24342898L,
                    586,
                    "Den of Nalorakk",
                    "DON",
                    22,
                    1,
                    OffsetDateTime.parse("2026-11-02T10:03:59.000Z"),
                    1752194L,
                    1920999L,
                    518.3F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/24342898-22-den-of-nalorakk",
                    listOf(Affix("Fortified"), Affix("Tyrannical"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23122135L,
                    250,
                    "Temple of Sethraliss",
                    "TOS",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-26T11:42:56.000Z"),
                    1770805L,
                    1920999L,
                    517.9F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23122135-22-temple-of-sethraliss",
                    listOf(Affix("Tyrannical"), Affix("Fortified"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23316701L,
                    584,
                    "The Blinding Vale",
                    "BV",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-27T11:54:58.000Z"),
                    1705102L,
                    1800999L,
                    517.0F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23316701-22-the-blinding-vale",
                    listOf(Affix("Tyrannical"), Affix("Fortified"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    22594220L,
                    585,
                    "Voidscar Arena",
                    "VSA",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-24T08:41:46.000Z"),
                    1749897L,
                    1800999L,
                    516.0F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/22594220-22-voidscar-arena",
                    listOf(Affix("Tyrannical"), Affix("Fortified"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23993428L,
                    399,
                    "Ruby Life Pools",
                    "RLP",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-31T13:40:59.000Z"),
                    1651151L,
                    1680999L,
                    515.6F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23993428-22-ruby-life-pools",
                    listOf(Affix("Fortified"), Affix("Tyrannical"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    23793908L,
                    249,
                    "Kings' Rest",
                    "KR",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-30T10:59:58.000Z"),
                    1952439L,
                    1980999L,
                    515.5F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/23793908-22-kings-rest",
                    listOf(Affix("Fortified"), Affix("Tyrannical"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    22907748L,
                    587,
                    "Murder Row",
                    "MR",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-25T13:15:30.000Z"),
                    2014699L,
                    2040999L,
                    515.5F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/22907748-22-murder-row",
                    listOf(Affix("Tyrannical"), Affix("Fortified"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                ),
                MythicPlusRun(
                    22880644L,
                    588,
                    "Altar of Fangs",
                    "AOF",
                    22,
                    1,
                    OffsetDateTime.parse("2026-10-25T10:31:19.000Z"),
                    1792576L,
                    1800999L,
                    515.2F,
                    "https://raider.io/mythic-plus-runs/season-mn-2/22880644-22-altar-of-fangs",
                    listOf(Affix("Tyrannical"), Affix("Fortified"), Affix("Xal'atath's Guile")),
                    ClassSpec(104, "Guardian", "tank")
                )
            ),
            equippedItemLevel = 335.0,
            guild = "De Tardes"
        )

    val mythicPlusRunJson = """
        {
            "keystone_run_id": 4462779,
            "map_challenge_mode_id": 456,
            "dungeon": "Throne of the Tides",
            "short_name": "TOTT",
            "mythic_level": 20,
            "num_keystone_upgrades": 2,
            "completed_at": "2023-11-25T12:12:22.000Z",
            "clear_time_ms": 1380287,
            "par_time_ms": 2040999,
            "score": 174.0,
            "url": "https://raider.io/mythic-plus-runs/season-df-3/4462779-20-throne-of-the-tides",
            "affixes": [
                { "name": "Tyrannical" },
                { "name": "Entangling" },
                { "name": "Bursting" }
            ]
        }
    """.trimIndent()

    val mythicPlusRun = MythicPlusRun(
        runId = 4462779L,
        dungeonId = 456,
        dungeon = "Throne of the Tides",
        shortName = "TOTT",
        keyLevel = 20,
        upgrades = 2,
        dateCompleted = OffsetDateTime.parse("2023-11-25T12:12:22.000Z"),
        clearTimeMs = 1380287L,
        dungeonTimeMs = 2040999L,
        score = 174.0F,
        url = "https://raider.io/mythic-plus-runs/season-df-3/4462779-20-throne-of-the-tides",
        affixes = listOf(Affix("Tyrannical"), Affix("Entangling"), Affix("Bursting"))
    )
}