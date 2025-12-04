package com.kos.staticdata

import kotlinx.serialization.Serializable

@Serializable
data class WowExpansion(
    val id: Int,
    val name: String,
    val isCurrentExpansion: Boolean
)