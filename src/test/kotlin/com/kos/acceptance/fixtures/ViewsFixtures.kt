package com.kos.acceptance.fixtures

import com.kos.views.Game
import com.kos.views.SimpleView
import com.kos.views.repository.ViewsDatabaseRepository
import org.jetbrains.exposed.sql.Database
import java.util.UUID

suspend fun givenView(
    db: Database,
    id: String = UUID.randomUUID().toString(),
    owner: String = "alice",
    name: String = "Test View",
    game: Game = Game.WOW,
    featured: Boolean = false,
): SimpleView = ViewsDatabaseRepository(db).create(
    id = id,
    name = name,
    owner = owner,
    entitiesIds = emptyList(),
    game = game,
    featured = featured,
    extraArguments = null
)
