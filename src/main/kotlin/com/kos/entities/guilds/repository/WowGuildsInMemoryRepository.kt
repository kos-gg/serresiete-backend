package com.kos.entities.guilds.repository

import com.kos.entities.guilds.realmOrNull
import com.kos.entities.guilds.WowForeverGuild
import com.kos.entities.guilds.WowGuild
import com.kos.entities.guilds.Guild
import com.kos.common.InMemoryRepository
import com.kos.views.Game

class WowGuildsInMemoryRepository() :
    WowGuildsRepository,
    InMemoryRepository {
    private val guilds: MutableList<Triple<Guild, String, Game>> = mutableListOf()

    override fun clear() {
        guilds.clear()
    }

    override suspend fun insertGuild(guild: Guild, viewId: String, game: Game) {
        val alreadyTracked = guilds.any { it.first.blizzardId == guild.blizzardId && it.third == game && it.second == viewId }
        if (!alreadyTracked) guilds.add(Triple(guild.lowercased(), viewId, game))
    }

    private fun Guild.lowercased(): Guild = when (this) {
        is WowGuild -> copy(name = name.lowercase(), realm = realm.lowercase(), region = region.lowercase())
        is WowForeverGuild -> copy(name = name.lowercase(), region = region.lowercase())
    }

    override suspend fun getWowGuilds(game: Game): List<Pair<WowGuild, String>> =
        guilds.filter { it.third == game }
            .mapNotNull { (guild, viewId, _) -> (guild as? WowGuild)?.let { it to viewId } }

    override suspend fun getForeverGuilds(): List<Pair<WowForeverGuild, String>> =
        guilds.filter { it.third == Game.WOW_FOREVER }
            .mapNotNull { (guild, viewId, _) -> (guild as? WowForeverGuild)?.let { it to viewId } }

    override suspend fun findTrackedGuild(
        name: String,
        realm: String?,
        region: String,
        game: Game
    ): Pair<Guild, String>? {
        return guilds.firstOrNull {
            it.third == game &&
                    it.first.name == name.lowercase() &&
                    it.first.realmOrNull() == realm?.lowercase() &&
                    it.first.region == region.lowercase()
        }?.let { it.first to it.second }
    }

    override suspend fun state(): WowGuildsState {
        return WowGuildsState(guilds)
    }

    override suspend fun withState(initialState: WowGuildsState): WowGuildsRepository {
        guilds.addAll(initialState.guilds)
        return this
    }
}
