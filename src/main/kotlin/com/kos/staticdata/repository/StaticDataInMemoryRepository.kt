package com.kos.staticdata.repository

import com.kos.common.InMemoryRepository
import com.kos.staticdata.WowExpansion

class StaticDataInMemoryRepository() : StaticDataRepository, InMemoryRepository {
    private val wowExpansions: MutableList<WowExpansion> = mutableListOf()

    override suspend fun getExpansions(): List<WowExpansion> {
        return wowExpansions
    }

    override suspend fun state(): StaticDataState {
        return StaticDataState(
            wowExpansions
        )
    }

    override suspend fun withState(initialState: StaticDataState): StaticDataRepository {
        wowExpansions.addAll(initialState.wowExpansions)
        return this
    }

    override fun clear() {
        wowExpansions.clear()
    }

}