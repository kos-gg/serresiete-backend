package com.kos.entities.guilds.repository

import com.kos.common.WithState
import com.kos.entities.guilds.Guild
import com.kos.entities.guilds.WowForeverGuild
import com.kos.entities.guilds.WowGuild
import com.kos.views.Game

interface WowGuildsRepository : WithState<WowGuildsState, WowGuildsRepository> {
    suspend fun insertGuild(guild: Guild, viewId: String, game: Game)

    suspend fun getWowGuilds(game: Game): List<Pair<WowGuild, String>>

    suspend fun getForeverGuilds(): List<Pair<WowForeverGuild, String>>

    suspend fun findTrackedGuild(name: String, realm: String?, region: String, game: Game): Pair<Guild, String>?
}

data class WowGuildsState(
    val guilds: List<Triple<Guild, String, Game>>
)
