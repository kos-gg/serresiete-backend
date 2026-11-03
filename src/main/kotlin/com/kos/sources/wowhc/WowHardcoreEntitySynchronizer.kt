package com.kos.sources.wowhc

import arrow.core.Either
import arrow.core.getOrElse
import arrow.core.raise.either
import arrow.core.raise.recover
import arrow.fx.coroutines.parMapUnordered
import com.kos.clients.ClientError
import com.kos.clients.NotFound
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.common.WithLogger
import com.kos.datacache.DataCache
import com.kos.datacache.domain.*
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.PartialFailure
import com.kos.entities.ThirdPartyCallFailed
import com.kos.entities.domain.Entity
import com.kos.entities.domain.WowEntity
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.sync.EntitySynchronizer
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.views.Game
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.launch
import kotlinx.serialization.SerializationException
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.modules.SerializersModule
import kotlinx.serialization.modules.polymorphic
import java.time.Duration
import java.time.OffsetDateTime
import com.kos.entities.domain.WowCharacter

class WowHardcoreEntitySynchronizer(
    private val dataCacheRepository: DataCacheRepository,
    private val entitiesRepository: EntitiesRepository,
    private val raiderIoClient: RaiderIoClient,
    private val blizzardClient: BlizzardClient,
    private val wowItemsDatabaseRepository: WowItemsDatabaseRepository,
    private val concurrency: Int = 10,
) : EntitySynchronizer, WithLogger("WowHardcoreEntitySynchronizer") {

    override val game: Game = Game.WOW_HC
    override val json: Json = Json {
        serializersModule = SerializersModule {
            polymorphic(Data::class) {
                subclass(WowHardcoreData::class, WowHardcoreData.serializer())
            }
        }
        ignoreUnknownKeys = true
        encodeDefaults = false
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    override suspend fun synchronize(entities: List<Entity>): List<PartialFailure> =
        coroutineScope {
            val wowEntities = entities.filterIsInstance<WowEntity>()

            val dataChannel = Channel<DataCache>()
            val errorChannel = Channel<PartialFailure>()
            val errors = mutableListOf<PartialFailure>()

            val errorsCollector = launch {
                errorChannel.consumeAsFlow()
                    .collect {
                        errors.add(it)
                    }
            }

            val dataCollector = launch {
                dataChannel.consumeAsFlow()
                    .buffer(50)
                    .collect {
                        dataCacheRepository.insert(listOf(it))
                        logger.info("Cached entity ${it.entityId}")
                    }
            }

            val start = OffsetDateTime.now()
            val cached = wowEntities
                .asFlow()
                .parMapUnordered(concurrency) { entity ->
                    synchronizeWowHcEntity(entity).mapLeft { ThirdPartyCallFailed(entity.id, it) }
                }
                .fold(0) { cached, result ->
                    result.fold(
                        ifLeft = {
                            errorChannel.send(it)
                            cached
                        },
                        ifRight = { data ->
                            data?.let {
                                dataChannel.send(it)
                                cached + 1
                            } ?: cached
                        }
                    )
                }

            errorChannel.close()
            dataChannel.close()

            errorsCollector.join()
            dataCollector.join()

            logger.info(
                "Finished caching Wow HC entities: $cached cached, " +
                        "${wowEntities.size - cached - errors.size} skipped (dead or removed), " +
                        "${errors.size} failed in ${Duration.between(start, OffsetDateTime.now()).toSeconds()}s"
            )

            errors
        }

    private suspend fun getNewestDataCacheEntry(entityId: Long): WowHardcoreData? =
        dataCacheRepository.get(entityId)
            .maxByOrNull { it.inserted }
            ?.let {
                try {
                    json.decodeFromString<WowHardcoreData>(it.data)
                } catch (e: SerializationException) {
                    logger.warn(
                        "Couldn't deserialize entity $entityId while trying to obtain newest cached record.\n${e.message}"
                    )
                    null
                }
            }

    private suspend fun synchronizeWowHcEntity(entity: WowEntity): Either<ClientError, DataCache?> {
        val newestDataCacheEntry = getNewestDataCacheEntry(entity.id)

        return if (newestDataCacheEntry?.isDead == true) Either.Right(null)
        else either {
            val character: WowCharacter? = blizzardClient.getClassicProfile(entity.region, entity.realm, entity.name)
                .getOrElse { if (it is NotFound) null else raise(it) }

            val hardcoreData: WowHardcoreData? = when (character) {
                null -> handleNotFoundHardcoreCharacter(newestDataCacheEntry, entity)
                else ->
                    if (deadCharacterHasBeenCreatedAgain(newestDataCacheEntry, entity, character)) {
                        markWowHardcoreCharacterAsDead(newestDataCacheEntry!!)
                    } else {
                        val avatar = blizzardClient.getCharacterMedia(
                            entity.region,
                            entity.realm,
                            entity.name
                        ).bind()

                        val equippedItems = blizzardClient.getCharacterEquipment(
                            entity.region,
                            entity.realm,
                            entity.name
                        ).bind()

                        val stats = blizzardClient.getCharacterStats(
                            entity.region,
                            entity.realm,
                            entity.name
                        ).bind()

                        val specializations = blizzardClient.getCharacterSpecializations(
                            entity.region,
                            entity.realm,
                            entity.name
                        ).bind()

                        val wowHeadEmbeddedTalents =
                            raiderIoClient.wowheadEmbeddedCalculator(entity.region, entity.realm, entity.name).getOrNull()

                        val existentItemsAndItemsToRequest =
                            getExistentItemsAndItemsToRequest(newestDataCacheEntry, equippedItems)

                        //TODO: BRING BACK RETRY WHEN IT PERFORMS BETTER.
                        val newItemsWithIcons =
                            getNewItemsWithIcons(existentItemsAndItemsToRequest, entity).bindAll()

                        character.toWowHardcoreData(
                            entity.region,
                            avatar,
                            existentItemsAndItemsToRequest.first + newItemsWithIcons,
                            stats,
                            WowTalents(wowHeadEmbeddedTalents, specializations)
                        )
                    }
            }

            hardcoreData?.let { DataCache(entity.id, json.encodeToString<Data>(it), OffsetDateTime.now(), Game.WOW_HC) }
        }
    }

    private fun deadCharacterHasBeenCreatedAgain(
        newestDataCacheEntry: WowHardcoreData?,
        entity: WowEntity,
        character: WowCharacter
    ): Boolean = newestDataCacheEntry != null && entity.blizzardId != character.id

    private suspend fun getNewItemsWithIcons(
        existentItemsAndItemsToRequest: Pair<List<WowItem>, List<WowEquippedItemRef>>,
        wowEntity: WowEntity
    ): List<Either<ClientError, WowItem>> =
        existentItemsAndItemsToRequest.second.map { equippedItem ->
            either {
                val item = recover({ blizzardClient.getItem(wowEntity.region, equippedItem.itemId).bind() }) { blizzardError ->
                    wowItemsDatabaseRepository.getItem(equippedItem.itemId) ?: raise(blizzardError)
                }

                val icon = blizzardClient.getItemMedia(wowEntity.region, equippedItem.itemId)
                    .getOrElse { wowItemsDatabaseRepository.getItemMedia(equippedItem.itemId) }

                item.toWowItem(equippedItem, icon)
            }
        }

    private fun getExistentItemsAndItemsToRequest(
        newestDataCacheEntry: WowHardcoreData?,
        equippedItems: List<WowEquippedItemRef>
    ): Pair<List<WowItem>, List<WowEquippedItemRef>> {
        val cachedItems = newestDataCacheEntry?.items.orEmpty().associateBy { it.id }
        return equippedItems.mapNotNull { cachedItems[it.itemId] } to equippedItems.filter { it.itemId !in cachedItems }
    }

    private suspend fun handleNotFoundHardcoreCharacter(
        newestCharacterDataCacheEntry: WowHardcoreData?,
        wowEntity: WowEntity
    ): WowHardcoreData? =
        newestCharacterDataCacheEntry
            ?.let { markWowHardcoreCharacterAsDead(it) }
            ?: run {
                logger.info(
                    "Character ${wowEntity.name} (${wowEntity.region}/${wowEntity.realm}, id ${wowEntity.id}) " +
                        "no longer exists in Blizzard and has no cached data, removing it"
                )
                entitiesRepository.delete(wowEntity.id)
                null
            }

    private fun markWowHardcoreCharacterAsDead(
        newestCharacterDataCacheEntry: WowHardcoreData
    ): WowHardcoreData {
        return newestCharacterDataCacheEntry.copy(isDead = true)
    }

}