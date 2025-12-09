package com.kos.sources.wow.staticdata.wowexpansion.repository

import com.kos.common.InMemoryRepository
import com.kos.sources.wow.staticdata.wowexpansion.WowExpansion

class WowExpansionInMemoryRepository() : WowExpansionRepository, InMemoryRepository {
    private val wowExpansions: MutableList<WowExpansion> = mutableListOf()

    override suspend fun getExpansions(): List<WowExpansion> {
        return wowExpansions
    }

    override suspend fun state(): WowExpansionState {
        return WowExpansionState(
            wowExpansions
        )
    }

    override suspend fun withState(initialState: WowExpansionState): WowExpansionRepository {
        wowExpansions.addAll(initialState.wowExpansions)
        return this
    }

    override fun clear() {
        wowExpansions.clear()
    }

}