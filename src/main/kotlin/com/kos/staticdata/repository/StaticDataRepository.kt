package com.kos.staticdata.repository

import com.kos.common.WithState
import com.kos.staticdata.WowExpansion

interface StaticDataRepository : WithState<StaticDataState, StaticDataRepository> {
    suspend fun getExpansions(): List<WowExpansion>
}

data class StaticDataState(
    val wowExpansions: List<WowExpansion>
)