package com.kos.sources.wowforever

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parMapUnordered
import com.kos.clients.ClientError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.common.WithLogger
import com.kos.datacache.DataCache
import com.kos.datacache.domain.Data
import com.kos.datacache.domain.WowForeverData
import com.kos.datacache.domain.WowItem
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.PartialFailure
import com.kos.entities.ThirdPartyCallFailed
import com.kos.entities.domain.Entity
import com.kos.entities.domain.WowForeverCharacter
import com.kos.entities.domain.WowForeverEntity
import com.kos.entities.sync.EntitySynchronizer
import com.kos.sources.wowforever.media.WowForeverMediaKind
import com.kos.sources.wowforever.media.WowForeverMediaRepository
import com.kos.views.Game
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.time.Duration
import java.time.OffsetDateTime

class WowForeverEntitySynchronizer(
    private val dataCacheRepository: DataCacheRepository,
    private val blizzardClient: BlizzardClient,
    private val mediaRepository: WowForeverMediaRepository,
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

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override suspend fun synchronize(entities: List<Entity>): List<PartialFailure> =
        coroutineScope {
            val wowForeverEntities = entities.filterIsInstance<WowForeverEntity>()

            val dataChannel = Channel<DataCache>()
            val errorChannel = Channel<PartialFailure>()

            val errorsCollector = async {
                errorChannel.consumeAsFlow()
                    .onEach { logger.error(it.error()) }
                    .toList()
            }

            val dataCollector = launch {
                dataChannel.consumeAsFlow()
                    .buffer(50)
                    .collect { data ->
                        dataCacheRepository.insert(listOf(data))
                        logger.info("Cached entity ${data.entityId}")
                    }
            }

            val start = OffsetDateTime.now()
            wowForeverEntities.asFlow()
                .parMapUnordered(concurrency) { entity ->
                    synchronizeEntity(entity).mapLeft { ThirdPartyCallFailed(entity.id, it) }
                }
                .collect { result ->
                    result.fold(
                        ifLeft = { errorChannel.send(it) },
                        ifRight = { dataChannel.send(it) }
                    )
                }

            errorChannel.close()
            dataChannel.close()

            val errors = errorsCollector.await()
            dataCollector.join()

            logger.info(
                "Finished caching Wow Forever entities: ${wowForeverEntities.size - errors.size} cached, " +
                        "${errors.size} failed in ${Duration.between(start, OffsetDateTime.now()).toSeconds()}s"
            )

            errors
        }

    private suspend fun synchronizeEntity(entity: WowForeverEntity): Either<ClientError, DataCache> = either {
        val character = blizzardClient.getForeverProfile(entity.region, entity.name, entity.surname).bind()
        val equipment = blizzardClient.getForeverEquipment(entity.region, entity.name, entity.surname)
            .onLeft { logger.warn("Couldn't fetch equipment of wow forever entity ${entity.id}: $it") }
            .getOrNull()

        val items = withStoredIcons(equipment?.map { it.toWowItem(null) } ?: previousItems(entity.id))

        mediaRepository.registerPending(
            WowForeverMediaKind.ITEM,
            entity.region,
            items.orEmpty().filter { it.icon == null }.map { it.id }.toSet()
        )

        DataCache(
            entity.id,
            json.encodeToString<Data>(character.toWowForeverData(entity.region, items)),
            OffsetDateTime.now(),
            Game.WOW_FOREVER
        )
    }

    private suspend fun withStoredIcons(items: List<WowItem>?): List<WowItem>? =
        items?.let {
            val icons = mediaRepository.getIcons(
                WowForeverMediaKind.ITEM,
                it.map { item -> item.id }.toSet()
            )
            it.map { item -> item.copy(icon = icons[item.id] ?: item.icon) }
        }

    private suspend fun previousItems(entityId: Long): List<WowItem>? =
        dataCacheRepository.get(entityId)
            .maxByOrNull { it.inserted }
            ?.let {
                try {
                    (json.decodeFromString<Data>(it.data) as? WowForeverData)?.items
                } catch (e: SerializationException) {
                    logger.warn("Couldn't deserialize newest cached record of wow forever entity $entityId: ${e.message}")
                    null
                }
            }

    private fun WowForeverCharacter.toWowForeverData(region: String, items: List<WowItem>?) = WowForeverData(
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
        lastLogin = lastLogin,
        items = items
    )
}
