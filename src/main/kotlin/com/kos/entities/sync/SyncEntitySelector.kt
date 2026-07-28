package com.kos.entities.sync

import com.kos.entities.domain.Entity
import com.kos.entities.sync.rules.StalenessSyncRule
import com.kos.views.Game


class SyncEntitySelector(
    private val stalenessSyncRule: StalenessSyncRule
) {
    suspend fun select(game: Game): List<Entity> = stalenessSyncRule.pick(game)
}
