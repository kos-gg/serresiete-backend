package com.kos.datacache.domain

import com.kos.common.OffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class MythicPlusRank(
    val world: Int,
    val region: Int,
    val realm: Int
)

@Serializable
data class MythicPlusRankWithSpecName(
    val name: String,
    val score: Double,
    val world: Int,
    val region: Int,
    val realm: Int
)

@Serializable
data class MythicPlusRanksWithSpecs(
    val overall: MythicPlusRank,
    val `class`: MythicPlusRank,
    val specs: List<MythicPlusRankWithSpecName>
)

@Serializable
data class Affix(
    val name: String
)

@Serializable
data class ClassSpec(
    val id: Int,
    val name: String,
    val role: String,
)

@Serializable
data class MythicPlusRun(
    val runId: Long,
    val dungeonId: Int,
    val dungeon: String,
    val shortName: String,
    val keyLevel: Int,
    val upgrades: Int,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val dateCompleted: OffsetDateTime,
    val clearTimeMs: Long,
    val dungeonTimeMs: Long,
    val score: Float,
    val url: String,
    val affixes: List<Affix>,
    val spec: ClassSpec? = null
)

@Serializable
data class RunMember(
    val name: String,
    val realm: String,
    val `class`: String,
    val spec: ClassSpec,
    val race: String,
    val itemLevel: Int
)

@Serializable
data class RunDetails(
    val members: List<RunMember>
)

@Serializable
data class EnrichedMythicPlusRun(
    val run: MythicPlusRun,
    val details: RunDetails? = null
)

@Serializable
@SerialName("wow")
data class WowData(
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
