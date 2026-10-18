package com.kos.sources.wowhc

import arrow.core.Either
import arrow.core.raise.either
import arrow.fx.coroutines.parMap
import com.kos.clients.ClientError
import com.kos.clients.HttpError
import com.kos.clients.blizzard.BlizzardClient
import com.kos.clients.raiderio.RaiderIoClient
import com.kos.clients.toSyncProcessingError
import com.kos.common.WithLogger
import com.kos.common._fold
import com.kos.common.error.ServiceError
import com.kos.common.error.SyncProcessingError
import com.kos.common.fold
import com.kos.common.split
import com.kos.datacache.DataCache
import com.kos.datacache.domain.*
import com.kos.datacache.repository.DataCacheRepository
import com.kos.entities.domain.Entity
import com.kos.entities.domain.WowEntity
import com.kos.entities.repository.EntitiesRepository
import com.kos.entities.sync.EntitySynchronizer
import com.kos.sources.wowhc.staticdata.wowitems.WowItemsDatabaseRepository
import com.kos.views.Game
import io.github.resilience4j.ratelimiter.RequestNotPermitted
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.launch
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
    override suspend fun synchronize(entities: List<Entity>): List<ServiceError> =
        coroutineScope {
            val wowEntities = entities.filterIsInstance<WowEntity>()

            val dataChannel = Channel<DataCache>()
            val errorChannel = Channel<ServiceError>()
            val errors = mutableListOf<ServiceError>()

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
            wowEntities
                .asFlow()
                .parMap(concurrency) { entity ->
                    synchronizeWowHcEntity(entity)
                }
                .collect { result ->
                    result.fold(
                        ifLeft = { errorChannel.send(it) },
                        ifRight = { it?.let { dataChannel.send(it) } }
                    )
                }

            errorChannel.close()
            dataChannel.close()

            errorsCollector.join()
            dataCollector.join()

            logger.info("Finished Caching Wow HC entities")
            logger.debug(
                "cached ${entities.size} entities in ${
                    Duration.between(start, OffsetDateTime.now()).toMinutes()
                } minutes"
            )

            errors
        }

    private suspend fun synchronizeWowHcEntity(entity: WowEntity): Either<ServiceError, DataCache?> =
        either {
            val newestDataCacheEntry: WowHardcoreData? =
                dataCacheRepository.get(entity.id).maxByOrNull {
                    it.inserted
                }?.let {
                    try {
                        json.decodeFromString<WowHardcoreData>(it.data)
                    } catch (e: Throwable) {
                        logger.debug(
                            "Couldn't deserialize entity ${entity.id} while trying to obtain newest cached record.\n${e.message}"
                        )
                        null
                    }
                }

            if (newestDataCacheEntry?.isDead == true) {
                return@either null
            }

            val hardcoreData: WowHardcoreData = blizzardClient.getClassicProfile(
                entity.region,
                entity.realm,
                entity.name
            ).fold(
                ifLeft = { error ->
                    when {
                        error is HttpError && error.status == 404 ->
                            handleNotFoundHardcoreCharacter(newestDataCacheEntry, entity)

                        else ->
                            Either.Left(error.toSyncProcessingError("getClassicProfile"))
                    }.bind()
                },
                ifRight = { character ->
                    if (deadCharacterHasBeenCreatedAgain(newestDataCacheEntry, entity, character)) {
                        markWowHardcoreCharacterAsDead(newestDataCacheEntry!!)
                    } else {
                        val avatar = execute("getCharacterMedia") {
                            blizzardClient.getCharacterMedia(
                                entity.region,
                                entity.realm,
                                entity.name
                            )
                        }.bind()

                        val equippedItems = execute("getCharacterEquipment") {
                            blizzardClient.getCharacterEquipment(
                                entity.region,
                                entity.realm,
                                entity.name
                            )
                        }.bind()

                        val stats = execute("getCharacterStats") {
                            blizzardClient.getCharacterStats(
                                entity.region,
                                entity.realm,
                                entity.name
                            )
                        }.bind()

                        val specializations = execute("getCharacterSpecializations") {
                            blizzardClient.getCharacterSpecializations(
                                entity.region,
                                entity.realm,
                                entity.name
                            )
                        }.bind()

                        val wowHeadEmbeddedTalents = execute("wowheadEmbeddedCalculator") {
                            raiderIoClient.wowheadEmbeddedCalculator(entity.region, entity.realm, entity.name)
                        }.getOrNull()

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
                })

            DataCache(entity.id, json.encodeToString<Data>(hardcoreData), OffsetDateTime.now(), Game.WOW_HC)
        }

    private fun deadCharacterHasBeenCreatedAgain(
        newestDataCacheEntry: WowHardcoreData?,
        entity: WowEntity,
        character: WowCharacter
    ): Boolean = newestDataCacheEntry != null && entity.blizzardId != character.id

    private suspend fun getNewItemsWithIcons(
        existentItemsAndItemsToRequest: Pair<List<WowItem>, List<WowEquippedItemRef>>,
        wowEntity: WowEntity
    ): List<Either<ServiceError, WowItem>> =
        existentItemsAndItemsToRequest.second.map { equippedItem ->
            either {
                val item = blizzardClient.getItem(wowEntity.region, equippedItem.itemId).fold(
                    ifLeft = {
                        execute("getItem") {
                            wowItemsDatabaseRepository.getItem(equippedItem.itemId)
                        }
                    },
                    ifRight = { Either.Right(it) }
                ).bind()

                val icon = blizzardClient.getItemMedia(
                    wowEntity.region,
                    equippedItem.itemId,
                ).fold(ifLeft = {
                    execute("getItemMedia") {
                        wowItemsDatabaseRepository.getItemMedia(equippedItem.itemId)
                    }
                }, ifRight = { Either.Right(it) }).getOrNull()

                item.toWowItem(equippedItem, icon)
            }
        }

    private fun getExistentItemsAndItemsToRequest(
        newestDataCacheEntry: WowHardcoreData?,
        equippedItems: List<WowEquippedItemRef>
    ) = newestDataCacheEntry._fold(
        left = { equippedItems.map { Either.Right(it) } },
        right = { record ->
            equippedItems.fold(emptyList<Either<WowItem, WowEquippedItemRef>>()) { acc, equippedItem ->
                when (val maybeItem =
                    record.items.find { equippedItem.itemId == it.id }) {
                    null -> acc + Either.Right(equippedItem)
                    else -> acc + Either.Left(maybeItem)
                }

            }
        }).split()

    private suspend fun handleNotFoundHardcoreCharacter(
        newestCharacterDataCacheEntry: WowHardcoreData?,
        wowEntity: WowEntity
    ): Either<ServiceError, WowHardcoreData> {
        return newestCharacterDataCacheEntry.fold(
            {
                entitiesRepository.delete(wowEntity.id)

                Either.Left(
                    SyncProcessingError(
                        "WOW_HARDCORE",
                        "Unable to sync character because no recent data was found in cache."
                    )
                )
            },
            {
                Either.Right(markWowHardcoreCharacterAsDead(it))
            })
    }

    private fun markWowHardcoreCharacterAsDead(
        newestCharacterDataCacheEntry: WowHardcoreData
    ): WowHardcoreData {
        return newestCharacterDataCacheEntry.copy(isDead = true)
    }

    private suspend fun <A> execute(
        operation: String,
        block: suspend () -> Either<ClientError, A>
    ): Either<ServiceError, A> =
        try {
            block().mapLeft { it.toSyncProcessingError(operation) }
        } catch (e: RequestNotPermitted) {
            Either.Left(SyncProcessingError(operation, "Rate limiter timeout: ${e.message}"))
        }

}