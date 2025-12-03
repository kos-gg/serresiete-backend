package com.kos.entities.repository

import arrow.core.Either
import com.kos.common.InMemoryRepository
import com.kos.common.InsertError
import com.kos.datacache.repository.DataCacheInMemoryRepository
import com.kos.entities.*
import com.kos.views.Game
import com.kos.views.repository.ViewsInMemoryRepository
import java.time.OffsetDateTime

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
}