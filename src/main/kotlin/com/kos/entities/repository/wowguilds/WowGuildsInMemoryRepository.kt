package com.kos.entities.repository.wowguilds

import arrow.core.Either
import com.kos.common.InMemoryRepository
import com.kos.common.error.InsertError
import com.kos.entities.GuildPayload

class WowGuildsInMemoryRepository() :
    WowGuildsRepository,
    InMemoryRepository {
    private val guilds: MutableList<Pair<GuildPayload, String>> = mutableListOf()

    override fun clear() {
        guilds.clear()
    }

    override suspend fun insertGuild(
        blizzardId: Long,
        name: String,
        realm: String,
        region: String,
        viewId: String
    ): Either<InsertError, Unit> {
        val guildPayload = GuildPayload(name, realm, region, blizzardId)
        return if (guilds.map { it.first }.indexOf(guildPayload) > 0) {
            Either.Left(InsertError("Duplicated guild $name $realm $region"))

        } else {
            guilds.add(guildPayload to viewId)
            Either.Right(Unit)
        }
    }

    override suspend fun getGuilds(): List<Pair<GuildPayload, String>> {
        return guilds
    }

    override suspend fun state(): WowGuildsState {
        return WowGuildsState(
            getGuilds()
        )
    }

    override suspend fun withState(initialState: WowGuildsState): WowGuildsRepository {
        guilds.addAll(initialState.guilds)
        return this
    }
}