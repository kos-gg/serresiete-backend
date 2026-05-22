package com.kos.clients.domain

import com.kos.clients.domain.raiderio.EnrichedMythicPlusRun
import com.kos.clients.domain.raiderio.MythicPlusRanksWithSpecs
import com.kos.clients.domain.raiderio.MythicPlusRun
import kotlinx.serialization.Serializable

@Serializable
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
