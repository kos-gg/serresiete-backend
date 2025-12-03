package com.kos.entities.repository

import arrow.core.Either
import com.kos.common.InsertError
import com.kos.entities.GuildPayload

interface WowGuildsRepository {
    suspend fun insertGuild(blizzardId: Long, name: String, realm: String, region: String, viewId: String): Either<InsertError, Unit>
    suspend fun getGuilds(): List<Pair<GuildPayload, String>>
}