package com.kos.sources.wow

import com.kos.datacache.domain.EnrichedMythicPlusRun
import com.kos.datacache.domain.MythicPlusRank
import com.kos.datacache.domain.MythicPlusRankWithSpecName
import com.kos.datacache.domain.MythicPlusRanksWithSpecs
import com.kos.datacache.domain.MythicPlusRun
import com.kos.datacache.domain.RunDetails
import com.kos.datacache.domain.WowData

data class SeasonCutoff(val totalPopulation: Int)

data class KeystoneBestRun(val dungeonId: Int, val keyLevel: Int, val timed: Boolean, val details: RunDetails)

data class MythicKeystoneProfile(val rating: Double, val bestRuns: List<KeystoneBestRun>)

data class WowCharacterProfile(
    val name: String,
    val realm: String,
    val region: String,
    val `class`: String,
    val spec: String,
    val score: Double,
    val overallRank: MythicPlusRank,
    val classRank: MythicPlusRank,
    val specRanks: List<MythicPlusRankWithSpecName>,
    val bestRuns: List<MythicPlusRun>,
    val recentRuns: List<MythicPlusRun>
) {
    fun toWowData(
        characterId: Long,
        quantile: Double?,
        bestRuns: List<EnrichedMythicPlusRun>,
    ) = WowData(
        characterId,
        name,
        realm,
        region,
        score,
        `class`,
        spec,
        quantile,
        MythicPlusRanksWithSpecs(overallRank, classRank, specRanks),
        bestRuns,
        recentRuns
    )
}
