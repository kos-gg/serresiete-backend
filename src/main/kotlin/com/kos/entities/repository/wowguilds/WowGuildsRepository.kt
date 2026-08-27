package com.kos.entities.repository.wowguilds

import arrow.core.Either
import com.kos.common.WithState
import com.kos.common.error.InsertError
import com.kos.entities.domain.GuildPayload
import com.kos.views.Game

interface WowGuildsRepository : WithState<WowGuildsState, WowGuildsRepository> {
    suspend fun insertGuild(
        blizzardId: Long,
        name: String,
        realm: String,
        region: String,
        viewId: String,
        game: Game
    ): Either<InsertError, Unit>

    suspend fun getGuilds(game: Game): List<Pair<GuildPayload, String>>
}

data class WowGuildsState(
    val guilds: List<Triple<GuildPayload, String, Game>>
)
