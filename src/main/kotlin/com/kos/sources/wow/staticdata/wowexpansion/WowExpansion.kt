package com.kos.sources.wow.staticdata.wowexpansion

import kotlinx.serialization.Serializable

@Serializable
data class WowExpansion(
    val id: Int,
    val name: String,
    val isCurrentExpansion: Boolean
)