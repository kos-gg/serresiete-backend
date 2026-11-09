package com.kos.sources.wowforever

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parMapUnordered
import com.kos.clients.ClientError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.common.DynamicCache
import com.kos.common.WithLogger
import com.kos.datacache.DataCache
import com.kos.datacache.domain.Data
import com.kos.datacache.domain.WowForeverData
import com.kos.datacache.domain.WowForeverSpecialization
import com.kos.datacache.domain.WowItem
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.PartialFailure
import com.kos.entities.ThirdPartyCallFailed
import com.kos.entities.domain.Entity
import com.kos.entities.domain.WowForeverCharacter
import com.kos.entities.domain.WowForeverEntity
import com.kos.entities.sync.EntitySynchronizer
import com.kos.sources.wowforever.media.WowForeverMedia
import com.kos.sources.wowforever.media.WowForeverMediaKind
import com.kos.sources.wowforever.media.WowForeverMediaRepository
import com.kos.views.Game
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.async
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

    @OptIn(FlowPreview::class)
    override suspend fun synchronize(entities: List<Entity>): List<PartialFailure> =
        coroutineScope {
            val wowForeverEntities = entities.filterIsInstance<WowForeverEntity>()

            val dataChannel = Channel<DataCache>()
            val iconCache = DynamicCache<Either<ClientError, String?>>()
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
                    synchronizeEntity(entity, iconCache).mapLeft { ThirdPartyCallFailed(entity.id, it) }
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
                        "${errors.size} failed in ${Duration.between(start, OffsetDateTime.now()).toSeconds()}s. " +
                        "Icon cache hit rate: ${"%.2f".format(iconCache.hitRate * 100)}%"
            )

            errors
        }

    private suspend fun synchronizeEntity(
        entity: WowForeverEntity,
        iconCache: DynamicCache<Either<ClientError, String?>>
    ): Either<ClientError, DataCache> = either {
        val character = blizzardClient.getForeverProfile(entity.region, entity.name, entity.surname).bind()
        val equipment = blizzardClient.getForeverEquipment(entity.region, entity.name, entity.surname)
            .onLeft { logger.warn("Couldn't fetch equipment of wow forever entity ${entity.id}: $it") }
            .getOrNull()
        val specializations = blizzardClient.getForeverSpecializations(entity.region, entity.name, entity.surname)
            .onLeft { logger.warn("Couldn't fetch specializations of wow forever entity ${entity.id}: $it") }
            .getOrNull()

        val cachedData = if (equipment == null || specializations == null) getCachedData(entity.id) else null

        val items = withItemIcons(entity.region, equipment?.map { it.toWowItem(null) } ?: cachedData?.items, iconCache)
        val talents = withTalentIcons(entity.region, specializations ?: cachedData?.talents, iconCache)

        DataCache(
            entity.id,
            json.encodeToString<Data>(character.toWowForeverData(entity.region, items, talents)),
            OffsetDateTime.now(),
            Game.WOW_FOREVER
        )
    }

    private suspend fun withItemIcons(
        region: String,
        items: List<WowItem>?,
        iconCache: DynamicCache<Either<ClientError, String?>>
    ): List<WowItem>? =
        items?.let {
            val icons = resolveIcons(
                WowForeverMediaKind.ITEM,
                region,
                it.map { item -> item.id }.toSet(),
                iconCache
            )
            it.map { item -> item.copy(icon = item.icon ?: icons[item.id]) }
        }

    private suspend fun withTalentIcons(
        region: String,
        specializations: List<WowForeverSpecialization>?,
        iconCache: DynamicCache<Either<ClientError, String?>>
    ): List<WowForeverSpecialization>? =
        specializations?.let {
            val icons = resolveIcons(
                WowForeverMediaKind.SPELL,
                region,
                it.flatMap { specialization -> specialization.talents }
                    .map { talent -> talent.spellId }
                    .toSet(),
                iconCache
            )
            it.map { specialization ->
                specialization.copy(
                    talents = specialization.talents.map { talent ->
                        talent.copy(
                            icon = talent.icon ?: icons[talent.spellId]
                        )
                    }
                )
            }
        }

    private suspend fun resolveIcons(
        kind: WowForeverMediaKind,
        region: String,
        ids: Set<Long>,
        iconCache: DynamicCache<Either<ClientError, String?>>
    ): Map<Long, String> =
        if (ids.isEmpty()) emptyMap()
        else {
            val storedIcons = mediaRepository.getIcons(kind, ids)
            val fetchedIcons = (ids - storedIcons.keys).mapNotNull { id ->
                fetchIcon(kind, region, id, iconCache)?.let { id to it }
            }
            storedIcons + fetchedIcons
        }

    private suspend fun fetchIcon(
        kind: WowForeverMediaKind,
        region: String,
        id: Long,
        iconCache: DynamicCache<Either<ClientError, String?>>
    ): String? =
        iconCache.get("$kind:$id") {
            fetchMedia(kind, region, id)
                .onLeft { logger.warn("Couldn't fetch icon of wow forever $kind $id: $it") }
                .onRight { icon -> icon?.let { mediaRepository.insert(listOf(WowForeverMedia(kind, id, it))) } }
        }.getOrNull()

    private suspend fun fetchMedia(kind: WowForeverMediaKind, region: String, id: Long): Either<ClientError, String?> =
        when (kind) {
            WowForeverMediaKind.ITEM -> blizzardClient.getForeverItemMedia(region, id)
            WowForeverMediaKind.SPELL -> blizzardClient.getForeverSpellMedia(region, id)
        }

    private suspend fun getCachedData(entityId: Long): WowForeverData? =
        dataCacheRepository.get(entityId)
            .maxByOrNull { it.inserted }
            ?.let {
                try {
                    json.decodeFromString<Data>(it.data) as? WowForeverData
                } catch (e: SerializationException) {
                    logger.warn("Couldn't deserialize newest cached record of wow forever entity $entityId: ${e.message}")
                    null
                }
            }

    private fun WowForeverCharacter.toWowForeverData(
        region: String,
        items: List<WowItem>?,
        talents: List<WowForeverSpecialization>?
    ) = WowForeverData(
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
        items = items,
        talents = talents
    )
}
