package com.kos.sources.wowforever

import arrow.core.Either
import arrow.core.raise.either
import arrow.core.raise.recover
import arrow.fx.coroutines.parMapUnordered
import com.kos.clients.ClientError
import com.kos.clients.NotFound
import com.kos.clients.blizzard.BlizzardClient
import com.kos.common.DynamicCache
import com.kos.common.WithLogger
import com.kos.datacache.DataCache
import com.kos.datacache.domain.*
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
        val profile = blizzardClient.getForeverProfile(entity.region, entity.name, entity.surname).bind()
        val cachedData = getCachedData(entity.id)

        val dataCache = cachedData
            ?.takeIf { profile.lastLogin.isEqual(it.lastLogin) }
            ?.let {
                profile.toWowForeverData(
                    entity.region,
                    it.items,
                    it.talents,
                    it.stats,
                    it.avatar,
                    it.pvp,
                    it.professions
                )
            }
            ?: fetchDetails(entity, profile, cachedData)

        DataCache(
            entity.id,
            json.encodeToString<Data>(
                dataCache.copy(
                    items = withItemEquipment(entity.region, dataCache.items, iconCache),
                    talents = withTalents(entity.region, dataCache.talents, iconCache),
                    professions = withProfessions(entity.region, dataCache.professions, iconCache)
                )
            ),
            OffsetDateTime.now(),
            Game.WOW_FOREVER
        )
    }

    private suspend fun withItemEquipment(
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

    private suspend fun withTalents(
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

    private suspend fun withProfessions(
        region: String,
        professions: WowForeverProfessions?,
        iconCache: DynamicCache<Either<ClientError, String?>>
    ): WowForeverProfessions? =
        professions?.let {
            val icons = resolveIcons(
                WowForeverMediaKind.PROFESSION,
                region,
                (it.primaries + it.secondaries).map { profession -> profession.id }.toSet(),
                iconCache
            )
            WowForeverProfessions(
                primaries = it.primaries.map { profession ->
                    profession.copy(
                        icon = profession.icon ?: icons[profession.id]
                    )
                },
                secondaries = it.secondaries.map { profession ->
                    profession.copy(
                        icon = profession.icon ?: icons[profession.id]
                    )
                }
            )
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
            WowForeverMediaKind.PROFESSION -> blizzardClient.getForeverProfessionMedia(region, id)
        }

    private suspend fun fetchDetails(
        entity: WowForeverEntity,
        profile: WowForeverCharacter,
        cachedData: WowForeverData?
    ): WowForeverData {
        val equipment = getEquipment(entity)
        val specializations = getSpecializations(entity)
        val statistics = getStats(entity)
        val avatar = getAvatar(entity)
        val pvp = getPvp(entity)
        val professions = getProfessions(entity)

        val foreverData = profile.toWowForeverData(
            entity.region,
            items = equipment.getOrNull()?.map { it.toWowItem(null) },
            talents = specializations.getOrNull(),
            stats = statistics.getOrNull(),
            avatar = avatar.getOrNull(),
            pvp = pvp.getOrNull(),
            professions = professions.getOrNull()
        ).orElse(cachedData)

        val everyDetailFetched =
            listOf(equipment, specializations, statistics, avatar, pvp, professions).all { it.isRight() }
        return if (everyDetailFetched) foreverData
        else foreverData.copy(lastLogin = cachedData?.lastLogin ?: foreverData.lastLogin)
    }

    private suspend fun getCachedData(entityId: Long): WowForeverData? =
        dataCacheRepository.getLatest(entityId, game)
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
        talents: List<WowForeverSpecialization>?,
        stats: WowStats?,
        avatar: String?,
        pvp: WowForeverPvp?,
        professions: WowForeverProfessions?
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
        talents = talents,
        stats = stats,
        avatar = avatar,
        pvp = pvp,
        professions = professions
    )

    private suspend fun getEquipment(entity: WowForeverEntity): Either<ClientError, List<WowForeverEquippedItem>?> =
        blizzardClient.getForeverEquipment(entity.region, entity.name, entity.surname)
            .missingWhenNotFound()
            .onLeft { logger.warn("Couldn't fetch equipment of wow forever entity ${entity.id}: $it") }

    private suspend fun getSpecializations(entity: WowForeverEntity): Either<ClientError, List<WowForeverSpecialization>?> =
        blizzardClient.getForeverSpecializations(entity.region, entity.name, entity.surname)
            .missingWhenNotFound()
            .onLeft { logger.warn("Couldn't fetch specializations of wow forever entity ${entity.id}: $it") }

    private suspend fun getStats(entity: WowForeverEntity): Either<ClientError, WowStats?> =
        blizzardClient.getForeverStatistics(entity.region, entity.name, entity.surname)
            .missingWhenNotFound()
            .onLeft { logger.warn("Couldn't fetch statistics of wow forever entity ${entity.id}: $it") }

    private suspend fun getAvatar(entity: WowForeverEntity): Either<ClientError, String?> =
        blizzardClient.getForeverAvatar(entity.region, entity.name, entity.surname)
            .missingWhenNotFound()
            .onLeft { logger.warn("Couldn't fetch avatar of wow forever entity ${entity.id}: $it") }

    private suspend fun getPvp(entity: WowForeverEntity): Either<ClientError, WowForeverPvp?> =
        blizzardClient.getForeverPvpSummary(entity.region, entity.name, entity.surname)
            .missingWhenNotFound()
            .onLeft { logger.warn("Couldn't fetch pvp summary of wow forever entity ${entity.id}: $it") }

    private suspend fun getProfessions(entity: WowForeverEntity): Either<ClientError, WowForeverProfessions?> =
        blizzardClient.getForeverProfessions(entity.region, entity.name, entity.surname)
            .missingWhenNotFound()
            .onLeft { logger.warn("Couldn't fetch professions of wow forever entity ${entity.id}: $it") }

    private fun <A> Either<ClientError, A>.missingWhenNotFound(): Either<ClientError, A?> =
        either { recover({ bind() }) { if (it is NotFound) null else raise(it) } }
}
