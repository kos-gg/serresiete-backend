package com.kos.sources.wowforever

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parMapUnordered
import com.kos.clients.ClientError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.common.WithLogger
import com.kos.common.split
import com.kos.datacache.DataCache
import com.kos.datacache.domain.Data
import com.kos.datacache.domain.WowForeverData
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.PartialFailure
import com.kos.entities.ThirdPartyCallFailed
import com.kos.entities.domain.Entity
import com.kos.entities.domain.WowForeverCharacter
import com.kos.entities.domain.WowForeverEntity
import com.kos.entities.sync.EntitySynchronizer
import com.kos.views.Game
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.toList
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.time.Duration
import java.time.OffsetDateTime

@OptIn(FlowPreview::class)
class WowForeverEntitySynchronizer(
    private val dataCacheRepository: DataCacheRepository,
    private val blizzardClient: BlizzardClient,
    private val concurrency: Int = 10,
) : EntitySynchronizer, WithLogger("WowForeverEntitySynchronizer") {

    override val game: Game = Game.WOW_FOREVER
    override val json: Json = Json {
        serializersModule = SerializersModule {
            polymorphic(Data::class) {
                subclass(WowForeverData::class, WowForeverData.serializer())
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    override suspend fun synchronize(entities: List<Entity>): List<PartialFailure> {
        val start = OffsetDateTime.now()
        val wowForeverEntities = entities.filterIsInstance<WowForeverEntity>()

        val (errors, cached) = wowForeverEntities.asFlow()
            .parMapUnordered(concurrency) { entity ->
                synchronizeEntity(entity).mapLeft { ThirdPartyCallFailed(entity.id, it) }
            }
            .toList()
            .split()

        dataCacheRepository.insert(cached)
        errors.forEach { logger.error(it.error()) }

        logger.info(
            "Finished caching Wow Forever entities: ${cached.size} cached, " +
                    "${errors.size} failed in ${Duration.between(start, OffsetDateTime.now()).toSeconds()}s"
        )

        return errors
    }

    private suspend fun synchronizeEntity(entity: WowForeverEntity): Either<ClientError, DataCache> = either {
        val character = blizzardClient.getForeverProfile(entity.region, entity.name, entity.surname).bind()
        val avatar = blizzardClient.getForeverCharacterMedia(entity.region, entity.name, entity.surname).bind()

        DataCache(
            entity.id,
            json.encodeToString<Data>(character.toWowForeverData(entity.region, avatar)),
            OffsetDateTime.now(),
            Game.WOW_FOREVER
        )
    }

    private fun WowForeverCharacter.toWowForeverData(region: String, avatar: String?) = WowForeverData(
        id = id,
        name = name,
        surname = surname,
        region = region,
        level = level,
        averageItemLevel = averageItemLevel,
        equippedItemLevel = equippedItemLevel,
        characterClass = characterClass,
        race = race,
        gender = gender,
        faction = faction,
        guild = guild,
        experience = experience,
        ruleset = ruleset,
        avatar = avatar,
        lastLogin = lastLogin
    )
}
