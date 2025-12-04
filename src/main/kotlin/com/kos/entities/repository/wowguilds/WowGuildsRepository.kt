package com.kos.entities.repository.wowguilds

import arrow.core.Either
import com.kos.common.InsertError
import com.kos.common.WithState
import com.kos.entities.GuildPayload

interface WowGuildsRepository : WithState<WowGuildsState, WowGuildsRepository> {
    suspend fun insertGuild(
        blizzardId: Long,
        name: String,
        realm: String,
        region: String,
        viewId: String
    ): Either<InsertError, Unit>

    suspend fun getGuilds(): List<Pair<GuildPayload, String>>
}

data class WowGuildsState(
    val guilds: List<Pair<GuildPayload, String>>
)
