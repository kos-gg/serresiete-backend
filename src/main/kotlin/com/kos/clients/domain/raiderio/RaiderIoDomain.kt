package com.kos.clients.domain.raiderio

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.JsonParseError
import com.kos.entities.domain.Spec
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.*

@Serializable
data class RaiderIoCutoff(val totalPopulation: Int)

object RaiderIoProtocol {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseCutoffJson(jsonString: String): RaiderIoCutoff {
        val totalPopulation =
            json.parseToJsonElement(jsonString)
                .jsonObject["cutoffs"]
                ?.jsonObject
                ?.get("p999")
                ?.jsonObject
                ?.get("all")
                ?.jsonObject
                ?.get("totalPopulationCount")
                ?.jsonPrimitive
                ?.int
                ?: throw IllegalStateException(
                    "cutoffs/p999/all/totalPopulationCount missing"
                )

        return RaiderIoCutoff(totalPopulation)
    }

    fun getMythicPlusRanks(
        profile: RaiderIoProfile,
        specs: List<Spec>
    ): Either<JsonParseError, List<MythicPlusRankWithSpecName>> =
        either {
            val ranksByExternalSpec = profile.mythicPlusRanks.specs

            specs.map { spec ->
                val rank = ranksByExternalSpec["spec_${spec.externalSpec}"]
                    ?: raise(
                        JsonParseError(
                            raw = "",
                            error = "/mythic_plus_ranks/spec_${spec.externalSpec}"
                        )
                    )

                MythicPlusRankWithSpecName(
                    name = spec.name,
                    score = profile.seasonScores[0].scores.specScore(spec.internalSpec),
                    world = rank.world,
                    region = rank.region,
                    realm = rank.realm
                )
            }
        }
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

data class RaiderIoResponse(
    val profile: RaiderIoProfile,
    val specs: List<MythicPlusRankWithSpecName>
)
