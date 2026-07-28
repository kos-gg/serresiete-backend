package com.kos.entities.sync.rules

import com.kos.entities.domain.Entity
import com.kos.views.Game

sealed interface SyncRule {
    suspend fun pick(game: Game): List<Entity>
}
