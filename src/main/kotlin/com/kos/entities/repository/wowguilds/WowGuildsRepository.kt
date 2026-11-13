package com.kos.entities.repository.wowguilds

import com.kos.common.WithState
import com.kos.entities.domain.WowForeverGuild
import com.kos.entities.domain.WowGuild
import com.kos.entities.domain.WowRealmGuild
import com.kos.views.Game

interface WowGuildsRepository : WithState<WowGuildsState, WowGuildsRepository> {
    suspend fun insertGuild(guild: WowGuild, viewId: String, game: Game)

    suspend fun getRealmGuilds(game: Game): List<Pair<WowRealmGuild, String>>

    suspend fun getForeverGuilds(): List<Pair<WowForeverGuild, String>>

    suspend fun findTrackedGuild(name: String, realm: String?, region: String, game: Game): Pair<WowGuild, String>?
}

data class WowGuildsState(
    val guilds: List<Triple<WowGuild, String, Game>>
)
