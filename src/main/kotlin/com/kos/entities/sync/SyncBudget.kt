package com.kos.entities.sync

import com.kos.views.Game

class SyncBudget(private val budgetForGame: Map<Game, Int>) {
    fun forGame(game: Game): Int = budgetForGame.getValue(game)
}