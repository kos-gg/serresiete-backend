package com.kos.entities.repository.wowguilds

import com.kos.common.InMemoryRepository
import com.kos.entities.domain.GuildPayload
import com.kos.views.Game

class WowGuildsInMemoryRepository() :
    WowGuildsRepository,
    InMemoryRepository {
    private val guilds: MutableList<Triple<GuildPayload, String, Game>> = mutableListOf()

    override fun clear() {
        guilds.clear()
    }

    override suspend fun insertGuild(
        blizzardId: Long,
        name: String,
        realm: String,
        region: String,
        viewId: String,
        game: Game
    ) {
        val guildPayload = GuildPayload(name.lowercase(), realm.lowercase(), region.lowercase(), blizzardId)
        val alreadyTracked = guilds.any { it.first.blizzardId == blizzardId && it.third == game && it.second == viewId }
        if (!alreadyTracked) guilds.add(Triple(guildPayload, viewId, game))
    }

    override suspend fun getGuilds(game: Game): List<Pair<GuildPayload, String>> {
        return guilds.filter { it.third == game }.map { it.first to it.second }
    }

    override suspend fun findTrackedGuild(
        name: String,
        realm: String,
        region: String,
        game: Game
    ): Pair<GuildPayload, String>? {
        return guilds.firstOrNull {
            it.third == game &&
                    it.first.name == name.lowercase() &&
                    it.first.realm == realm.lowercase() &&
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
