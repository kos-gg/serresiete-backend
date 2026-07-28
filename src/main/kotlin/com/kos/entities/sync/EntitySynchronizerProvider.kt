package com.kos.entities.sync

import com.kos.views.Game

class EntitySynchronizerProvider(
    private val synchronizes: List<EntitySynchronizer>
) {

    fun synchronizerFor(game: Game): EntitySynchronizer? =
        synchronizes.firstOrNull { it.game == game }
}
