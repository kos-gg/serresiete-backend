package com.kos.entities.repository.wowguilds

import arrow.core.Either
import com.kos.common.InMemoryRepository
import com.kos.common.error.InsertError
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
    ): Either<InsertError, Unit> {
        val guildPayload = GuildPayload(name, realm, region, blizzardId)
        val existingViewId = guilds.firstOrNull { it.first == guildPayload && it.third == game }?.second
        return when {
            existingViewId == viewId -> Either.Right(Unit)
            existingViewId != null -> Either.Left(InsertError("Duplicated guild $name $realm $region"))
            else -> {
                guilds.add(Triple(guildPayload, viewId, game))
                Either.Right(Unit)
            }
        }
    }

    override suspend fun getGuilds(game: Game): List<Pair<GuildPayload, String>> {
        return guilds.filter { it.third == game }.map { it.first to it.second }
    }

    override suspend fun state(): WowGuildsState {
        return WowGuildsState(guilds)
    }

    override suspend fun withState(initialState: WowGuildsState): WowGuildsRepository {
        guilds.addAll(initialState.guilds)
        return this
    }
}
