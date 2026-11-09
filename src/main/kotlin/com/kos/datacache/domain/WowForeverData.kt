package com.kos.datacache.domain

import com.kos.common.OffsetDateTimeSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.OffsetDateTime

@Serializable
data class WowForeverTalent(
    val name: String,
    val spellId: Long,
    val rank: Int,
    val description: String,
    val icon: String?
)

@Serializable
data class WowForeverSpecialization(
    val name: String,
    val points: Int,
    val talents: List<WowForeverTalent>
)

@Serializable
@SerialName("wow_forever")
data class WowForeverData(
    val id: Long,
    val name: String,
    val surname: String,
    val region: String,
    val level: Int,
    val averageItemLevel: Int,
    val equippedItemLevel: Int,
    val characterClass: String,
    val race: String,
    val gender: String,
    val faction: String,
    val guild: String?,
    val experience: Int,
    val ruleset: String,
    @Serializable(with = OffsetDateTimeSerializer::class)
    val lastLogin: OffsetDateTime,
    val items: List<WowItem>? = null,
    val talents: List<WowForeverSpecialization>? = null,
    val stats: WowStats? = null,
    val avatar: String? = null
) : Data {
    fun isComplete(): Boolean = listOf(items, talents, stats, avatar).all { it != null }

    fun orElse(cached: WowForeverData?): WowForeverData = copy(
        items = items ?: cached?.items,
        talents = talents ?: cached?.talents,
        stats = stats ?: cached?.stats,
        avatar = avatar ?: cached?.avatar
    )
}
