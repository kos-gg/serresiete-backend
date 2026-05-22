package com.kos.clients.domain.blizzard

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class TalentInfo(
    val id: Int
)

@Serializable
data class Talent(
    val talent: TalentInfo,
    @SerialName("talent_rank")
    val talentRank: Int
)

@Serializable
data class Specialization(
    @SerialName("specialization_name")
    val specializationName: String,
    @SerialName("spent_points")
    val spentPoints: Int,
    val talents: List<Talent>
)

@Serializable
data class SpecializationGroup(
    val specializations: List<Specialization>? = null,
    @SerialName("is_active")
    val isActive: Boolean
)

@Serializable
data class GetWowSpecializationsResponse(
    @SerialName("specialization_groups")
    val specializationGroups: List<SpecializationGroup>
)
