package com.kos.entities.sync.rules

import com.kos.entities.domain.Entity
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.sync.SyncBudget
import com.kos.views.Game

class StalenessSyncRule(
    private val entitiesRepository: EntitiesRepository,
    private val olderThanMinutes: Long,
    private val syncBudget: SyncBudget,
) : SyncRule {

    override suspend fun pick(game: Game): List<Entity> =
        entitiesRepository.getEntitiesOlderThan(game, olderThanMinutes, syncBudget.forGame(game))
}
