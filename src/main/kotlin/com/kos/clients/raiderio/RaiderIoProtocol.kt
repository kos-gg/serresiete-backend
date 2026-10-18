package com.kos.clients.raiderio

import arrow.core.Either
import arrow.core.raise.either
import com.kos.clients.domain.RaiderIoProfile
import com.kos.datacache.domain.MythicPlusRankWithSpecName
import com.kos.entities.domain.Spec
import com.kos.sources.wow.SeasonCutoff
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

object RaiderIoProtocol {

    private val json = Json {
        ignoreUnknownKeys = true
    }

    fun parseCutoffJson(jsonString: String): SeasonCutoff {
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

        return SeasonCutoff(totalPopulation)
    }

    fun getMythicPlusRanks(
        profile: RaiderIoProfile,
        specs: List<Spec>
    ): Either<com.kos.clients.JsonParseError, List<MythicPlusRankWithSpecName>> =
        either {
            val ranksByExternalSpec = profile.mythicPlusRanks.specs

            specs.map { spec ->
                val rank = ranksByExternalSpec["spec_${spec.externalSpec}"]
                    ?: raise(
                        com.kos.clients.JsonParseError(
                            raw = "",
                            error = "/mythic_plus_ranks/spec_${spec.externalSpec}"
                        )
                    )

                val score = when (spec.internalSpec) {
                    0 -> profile.seasonScores[0].scores.spec0
                    1 -> profile.seasonScores[0].scores.spec1
                    2 -> profile.seasonScores[0].scores.spec2
                    3 -> profile.seasonScores[0].scores.spec3
                    else -> 0.0
                }

                MythicPlusRankWithSpecName(
                    name = spec.name,
                    score = score,
                    world = rank.world,
                    region = rank.region,
                    realm = rank.realm
                )
            }
        }

}
