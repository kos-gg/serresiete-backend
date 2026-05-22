package com.kos.clients.domain.raiderio

import com.kos.common.OffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

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
    @SerialName("character_id")
    val characterId: Long,
    @SerialName("approximate_died_at")
    val approximateDiedAt: Int,
    @SerialName("logged_encounter_id")
    val loggedEncounterId: Int? = null
)

@Serializable
data class LoggedDetails(val deaths: List<RunDetailsDeath> = emptyList())

@Serializable
data class RunDetails(
    val roster: List<RunDetailsRosterEntry>,
    @SerialName("logged_details")
    val loggedDetails: LoggedDetails? = null
) {
    val deathCount: Int get() = loggedDetails?.deaths?.size ?: 0
}

@Serializable
data class EnrichedMythicPlusRun(
    val run: MythicPlusRun,
    val details: RunDetails? = null
)
