package com.kos.datacache.domain

import com.kos.common.OffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

data class SeasonCutoff(val totalPopulation: Int)

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
    @SerialName("name")
    val affix: String
)

@Serializable
data class ClassSpec(
    val id: Int,
    val name: String,
    val role: String,
)

@Serializable
data class MythicPlusRun(
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
    val affixes: List<Affix>,
    val spec: ClassSpec? = null
)

@Serializable
data class RunDetailsCharacterClass(val name: String)

@Serializable
data class RunDetailsCharacterSpec(val name: String)

@Serializable
data class RunDetailsCharacterRealm(
    val id: Int,
    val name: String,
    val slug: String
)

@Serializable
data class RunDetailsCharacterRegion(
    val name: String,
    @SerialName("short_name")
    val shortName: String,
    val slug: String
)

@Serializable
data class RunDetailsRosterRanks(
    val score: Double
)

@Serializable
data class RunDetailsCharacter(
    val name: String,
    val `class`: RunDetailsCharacterClass,
    val spec: RunDetailsCharacterSpec,
    val realm: RunDetailsCharacterRealm,
    val region: RunDetailsCharacterRegion
)

@Serializable
data class RunDetailsRosterEntry(
    val character: RunDetailsCharacter,
    val role: String,
    val ranks: RunDetailsRosterRanks
)

@Serializable
data class RunDetailsDeath(
    @SerialName("approximate_died_at")
    val approximateDiedAt: Int
)

@Serializable
data class LoggedDetails(val deaths: List<RunDetailsDeath> = emptyList())

@Serializable
data class RunDetails(
    val roster: List<RunDetailsRosterEntry>,
    @SerialName("logged_details")
    val loggedDetails: LoggedDetails? = null
)

@Serializable
data class EnrichedMythicPlusRun(
    val run: MythicPlusRun,
    val details: RunDetails? = null
)

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
    fun toRaiderIoData(
        characterId: Long,
        quantile: Double?,
        bestRuns: List<EnrichedMythicPlusRun>,
    ) = RaiderIoData(
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
