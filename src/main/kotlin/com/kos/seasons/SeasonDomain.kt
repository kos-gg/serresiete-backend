package com.kos.seasons

import kotlinx.serialization.Serializable

@Serializable
sealed interface GameSeason {
    val id: Int
    val name: String
    fun same(other: GameSeason): Boolean
}