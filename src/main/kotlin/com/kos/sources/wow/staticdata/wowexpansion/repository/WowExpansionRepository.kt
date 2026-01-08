package com.kos.sources.wow.staticdata.wowexpansion.repository

import com.kos.common.WithState
import com.kos.sources.wow.staticdata.wowexpansion.WowExpansion

interface WowExpansionRepository : WithState<WowExpansionState, WowExpansionRepository> {
    suspend fun getExpansions(): List<WowExpansion>
}

data class WowExpansionState(
    val wowExpansions: List<WowExpansion>
)