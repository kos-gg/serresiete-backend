package com.kos.datacache

import com.kos.views.Game

class EntitySynchronizerProvider(
    private val synchronizers: List<EntitySynchronizer>
) {

    fun synchronizerFor(game: Game): EntitySynchronizer? =
        synchronizers.firstOrNull { it.game == game }
}
