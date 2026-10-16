package com.kos.datacache.domain

import com.kos.clients.domain.MythicPlusRank
import com.kos.clients.domain.MythicPlusRankWithSpecName
import com.kos.clients.domain.MythicPlusRun
import com.kos.clients.domain.RunDetails
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class MythicPlusRanksWithSpecs(
    val overall: MythicPlusRank,
    val `class`: MythicPlusRank,
    val specs: List<MythicPlusRankWithSpecName>
)

@Serializable
data class EnrichedMythicPlusRun(
    val run: MythicPlusRun,
    val details: RunDetails? = null
)

@Serializable
@SerialName("com.kos.clients.domain.RaiderIoData")
data class RaiderIoData(
    val id: Long,
    val name: String,
    val realm: String,
    val region: String,
    val score: Double,
    val `class`: String,
    val spec: String,
    val quantile: Double?,
    val mythicPlusRanks: MythicPlusRanksWithSpecs,
    val mythicPlusBestRuns: List<EnrichedMythicPlusRun>,
    val mythicPlusRecentRuns: List<MythicPlusRun> = emptyList()
) : Data
