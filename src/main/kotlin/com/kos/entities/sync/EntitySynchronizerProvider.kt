package com.kos.entities.sync

import com.kos.views.Game

class EntitySynchronizerProvider(
    private val wowSynchronizer: EntitySynchronizer,
    private val wowHardcoreSynchronizer: EntitySynchronizer,
    private val lolSynchronizer: EntitySynchronizer
) {
    fun synchronizerFor(game: Game): EntitySynchronizer = when (game) {
        Game.WOW -> wowSynchronizer
        Game.WOW_HC -> wowHardcoreSynchronizer
        Game.LOL -> lolSynchronizer
    }
}
