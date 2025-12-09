package com.kos.datacache

import com.kos.common.HttpError
import com.kos.entities.domain.Entity
import com.kos.views.Game
import kotlinx.serialization.json.Json

interface EntitySynchronizer {
    val game: Game
    val json: Json
    suspend fun synchronize(entities: List<Entity>): List<HttpError>
}