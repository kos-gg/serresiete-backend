package com.kos.clients.domain.blizzard

import kotlinx.serialization.Serializable

@Serializable
data class WowCharacterResponse(
    val name: String,
    val level: Int
)

@Serializable
data class WowMemberResponse(
    val character: WowCharacterResponse
)

@Serializable
data class WowGuildResponse(
    val id: Long
)

@Serializable
data class GetWowRosterResponse(
    val members: List<WowMemberResponse>,
    val guild: WowGuildResponse
)
