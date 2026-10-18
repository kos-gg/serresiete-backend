package com.kos.clients.domain

import com.kos.common.OffsetDateTimeSerializer
import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.descriptors.buildClassSerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*
import java.time.OffsetDateTime

@Serializable
data class SeasonResponse(
    @SerialName("is_main_season")
    val isCurrentSeason: Boolean,
    val name: String,
    @SerialName("slug")
    val slug: String,
    @SerialName("blizzard_season_id")
    val blizzardSeasonId: Int,
    val dungeons: List<DungeonResponse>
)

@Serializable
data class DungeonResponse(
    val name: String,
    @SerialName("short_name")
    val shortName: String,
    @SerialName("challenge_mode_id")
    val dungeonId: Int,
    @SerialName("icon_url")
    val icon: String,
)

@Serializable
data class ExpansionSeasons(
    @Serializable
    val seasons: List<SeasonResponse>
)

@Serializable
data class SeasonScores(
    val all: Double,
    @SerialName("spec_0")
    val spec0: Double,
    @SerialName("spec_1")
    val spec1: Double,
    @SerialName("spec_2")
    val spec2: Double,
    @SerialName("spec_3")
    val spec3: Double,
)

@Serializable
data class MythicPlusSeasonScore(
    val season: String,
    val scores: SeasonScores,
)

@Serializable
data class MythicPlusRankResponse(
    val world: Int,
    val region: Int,
    val realm: Int
)

@Serializable(with = MythicPlusRanksSerializer::class)
data class MythicPlusRanks(
    val overall: MythicPlusRankResponse,
    val `class`: MythicPlusRankResponse,
    val specs: Map<String, MythicPlusRankResponse>
)

object MythicPlusRanksSerializer : KSerializer<MythicPlusRanks> {

    override val descriptor: SerialDescriptor =
        buildClassSerialDescriptor("MythicPlusRanks") {
            element("overall", MythicPlusRankResponse.serializer().descriptor)
            element("class", MythicPlusRankResponse.serializer().descriptor)
            element("specs", MapSerializer(String.serializer(), MythicPlusRankResponse.serializer()).descriptor)
        }

    override fun deserialize(decoder: Decoder): MythicPlusRanks {
        require(decoder is JsonDecoder)

        val jsonObject = decoder.decodeJsonElement().jsonObject

        val overall = jsonObject["overall"]
            ?: error("Missing overall rank")

        val clazz = jsonObject["class"]
            ?: error("Missing class rank")

        val specs = jsonObject
            .filterKeys { it.startsWith("spec_") }
            .mapValues { (_, value) ->
                decoder.json.decodeFromJsonElement(MythicPlusRankResponse.serializer(), value)
            }

        return MythicPlusRanks(
            overall = decoder.json.decodeFromJsonElement(overall),
            `class` = decoder.json.decodeFromJsonElement(clazz),
            specs = specs
        )
    }

    override fun serialize(encoder: Encoder, value: MythicPlusRanks) {
        require(encoder is JsonEncoder)

        val jsonObject = buildJsonObject {
            put("overall", encoder.json.encodeToJsonElement(value.overall))
            put("class", encoder.json.encodeToJsonElement(value.`class`))
            value.specs.forEach { (key, rank) ->
                put(key, encoder.json.encodeToJsonElement(rank))
            }
        }

        encoder.encodeJsonElement(jsonObject)
    }
}

@Serializable
data class AffixResponse(
    @SerialName("name")
    val affix: String
)

@Serializable
data class ClassSpecResponse(
    val id: Int,
    val name: String,
    val role: String,
)

@Serializable
data class MythicPlusRunResponse(
    @SerialName("keystone_run_id")
    val runId: Long,
    val dungeon: String,
    @SerialName("short_name")
    val shortName: String,
    @SerialName("mythic_level")
    val keyLevel: Int,
    @SerialName("num_keystone_upgrades")
    val upgrades: Int,
    @SerialName("completed_at")
    @Serializable(with = OffsetDateTimeSerializer::class)
    val dateCompleted: OffsetDateTime,
    @SerialName("clear_time_ms")
    val clearTimeMs: Long,
    @SerialName("par_time_ms")
    val dungeonTimeMs: Long,
    val score: Float,
    val url: String,
    val affixes: List<AffixResponse>,
    val spec: ClassSpecResponse? = null
)

@Serializable
data class RaiderIoScoreResponse(
    @SerialName("mythic_plus_scores_by_season")
    val seasonScores: List<MythicPlusSeasonScore>
)

@Serializable
data class RaiderIoProfile(
    val name: String,
    val realm: String,
    val region: String,
    val `class`: String,
    @SerialName("active_spec_name")
    val spec: String,
    @SerialName("mythic_plus_scores_by_season")
    val seasonScores: List<MythicPlusSeasonScore>,
    @SerialName("mythic_plus_ranks")
    val mythicPlusRanks: MythicPlusRanks,
    @SerialName("mythic_plus_best_runs")
    val mythicPlusBestRuns: List<MythicPlusRunResponse>,
    @SerialName("mythic_plus_recent_runs")
    val mythicPlusRecentRuns: List<MythicPlusRunResponse> = emptyList()
)

@Serializable
data class RunDetailsCharacterClassResponse(val name: String)

@Serializable
data class RunDetailsCharacterSpecResponse(val name: String)

@Serializable
data class RunDetailsCharacterRealmResponse(
    val id: Int,
    val name: String,
    val slug: String
)

@Serializable
data class RunDetailsCharacterRegionResponse(
    val name: String,
    @SerialName("short_name")
    val shortName: String,
    val slug: String
)

@Serializable
data class RunDetailsRosterRanksResponse(
    val score: Double
)

@Serializable
data class RunDetailsCharacterResponse(
    val name: String,
    val `class`: RunDetailsCharacterClassResponse,
    val spec: RunDetailsCharacterSpecResponse,
    val realm: RunDetailsCharacterRealmResponse,
    val region: RunDetailsCharacterRegionResponse
)

@Serializable
data class RunDetailsRosterEntryResponse(
    val character: RunDetailsCharacterResponse,
    val role: String,
    val ranks: RunDetailsRosterRanksResponse
)

@Serializable
data class RunDetailsDeathResponse(
    @SerialName("approximate_died_at")
    val approximateDiedAt: Int
)

@Serializable
data class LoggedDetailsResponse(val deaths: List<RunDetailsDeathResponse> = emptyList())

@Serializable
data class RunDetailsResponse(
    val roster: List<RunDetailsRosterEntryResponse>,
    @SerialName("logged_details")
    val loggedDetails: LoggedDetailsResponse? = null
) {
}

object CodeExtractorSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("CodeExtractor", PrimitiveKind.STRING)

    override fun deserialize(decoder: Decoder): String {
        require(decoder is JsonDecoder)
        val jsonObject = decoder.decodeJsonElement().jsonObject
        return jsonObject["code"]!!.jsonPrimitive.content
    }

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }
}

@Serializable
data class TalentLoadout(
    @Serializable(with = CodeExtractorSerializer::class)
    val wowheadCalculator: String
)

@Serializable
data class RaiderioWowHeadEmbeddedResponse(
    val talentLoadout: TalentLoadout
)
