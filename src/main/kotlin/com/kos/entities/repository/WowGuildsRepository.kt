package com.kos.entities.repository

import com.kos.entities.GuildPayload

interface WowGuildsRepository {
    suspend fun insertGuild(blizzardId: Long, name: String, realm: String, region: String, viewId: String)
    suspend fun getGuilds(): List<Pair<GuildPayload, String>>
}