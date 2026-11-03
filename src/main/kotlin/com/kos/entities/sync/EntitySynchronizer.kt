package com.kos.entities.sync

import com.kos.entities.PartialFailure
import com.kos.entities.domain.Entity
import com.kos.views.Game
import kotlinx.serialization.json.Json

interface EntitySynchronizer {
    val game: Game
    val json: Json
    suspend fun synchronize(entities: List<Entity>): List<PartialFailure>
}